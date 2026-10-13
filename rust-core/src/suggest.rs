use crate::dictionary::{normalize, Dictionary};

fn levenshtein(a: &str, b: &str) -> usize {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let m = a_chars.len();
    let n = b_chars.len();

    if m == 0 {
        return n;
    }
    if n == 0 {
        return m;
    }

    let mut prev = (0..=n).collect::<Vec<_>>();
    let mut curr = vec![0; n + 1];

    for i in 1..=m {
        curr[0] = i;
        for j in 1..=n {
            let cost = if a_chars[i - 1] == b_chars[j - 1] { 0 } else { 1 };
            curr[j] = (prev[j] + 1)
                .min(curr[j - 1] + 1)
                .min(prev[j - 1] + cost);
        }
        prev.copy_from_slice(&curr);
    }

    prev[n]
}

pub fn get_suggestions(dict: &Dictionary, prev_word: &str, prefix: &str) -> Vec<String> {
    let clean_prefix = prefix.trim();
    let prev_norm = normalize(prev_word.trim());

    if clean_prefix.is_empty() {
        // Sugestões contextuais baseadas na palavra anterior (bigramas)
        if !prev_norm.is_empty() {
            if let Some(next_list) = dict.bigrams_by_prev.get(&prev_norm) {
                let mut sorted = next_list.clone();
                sorted.sort_by(|a, b| b.1.cmp(&a.1));
                return sorted
                    .into_iter()
                    .map(|(w, _)| w)
                    .take(3)
                    .collect();
            }
        }
        // Se não houver contexto, retorna as 3 palavras mais frequentes
        let mut sorted_unigrams = dict.unigrams.clone();
        sorted_unigrams.sort_by(|a, b| b.freq.cmp(&a.freq));
        return sorted_unigrams
            .into_iter()
            .take(3)
            .map(|u| u.exact)
            .collect();
    }

    let prefix_norm = normalize(clean_prefix);
    let prefix_lower = clean_prefix.to_lowercase();

    // Buscar bigramas que podem impulsionar palavras candidatas
    let bigram_bonuses: std::collections::HashMap<String, u32> = if !prev_norm.is_empty() {
        dict.bigrams_by_prev
            .get(&prev_norm)
            .map(|list| {
                list.iter()
                    .map(|(w, weight)| (w.to_lowercase(), *weight as u32 * 3))
                    .collect()
            })
            .unwrap_or_default()
    } else {
        std::collections::HashMap::new()
    };

    let mut scored_candidates: Vec<(String, u32)> = Vec::new();

    for u in &dict.unigrams {
        if u.norm.starts_with(&prefix_norm) {
            let mut score = u.freq as u32;

            // Bônus de bigrama se a palavra anterior prevê este termo
            if let Some(&b_score) = bigram_bonuses.get(&u.exact.to_lowercase()) {
                score += b_score;
            }

            // Bônus alto para correspondência exata desconsiderando acento (ex: 'voce' -> 'você')
            if u.norm == prefix_norm {
                score += 50_000;
            }

            // Bônus máximo se digitou exatamente igual
            if u.exact.to_lowercase() == prefix_lower {
                score += 80_000;
            }

            scored_candidates.push((u.exact.clone(), score));
        }
    }

    // Ordenar decrescente pelo score
    scored_candidates.sort_by(|a, b| b.1.cmp(&a.1));

    // Deduplicar mantendo a ordem
    let mut results = Vec::with_capacity(3);
    for (cand, _) in scored_candidates {
        if !results.contains(&cand) {
            results.push(cand);
            if results.len() == 3 {
                break;
            }
        }
    }

    results
}

pub fn get_auto_correction(dict: &Dictionary, prev_word: &str, typed_word: &str) -> Option<String> {
    let clean = typed_word.trim();
    if clean.len() < 3 {
        return None;
    }

    let typed_lower = clean.to_lowercase();

    // 0. Stoplist e palavras estritamente protegidas contra autocorreção
    let stoplist = [
        "francois", "françois", "roma", "victor", "max",
        "ai", "esta", "festa", "algo", "calor", "ainda", "todo", "toda", "todos", "todas",
        "pois", "malha", "abraço", "abraços", "pra", "tbm", "vc", "pq", "blz", "bjs", "kkk", "kkkk",
        "avó", "avô", "está", "pode", "pôde", "vem", "vêm", "por", "pôr", "de", "que", "não",
        "bebe", "bebê", "pais", "país", "nós", "robôs", "vocês", "pés"
    ];
    if stoplist.contains(&typed_lower.as_str()) {
        return None;
    }

    // 1. Conservador: Se já for uma palavra válida exatamente no dicionário, NÃO corrige!
    if dict.is_word(&typed_lower) {
        return None;
    }

    let is_short_word = clean.chars().count() <= 4;
    let typed_norm = normalize(clean);

    // 2. Correção de acento: se digitou sem acento (ex: 'nao', 'voce', 'ate', 'tambem'),
    // e o dicionário possui a palavra com acento, essa é a correção mais natural e segura.
    let accent_matches: Vec<&crate::dictionary::Unigram> = dict.unigrams
        .iter()
        .filter(|u| u.norm == typed_norm)
        .collect();

    if accent_matches.len() == 1 {
        let candidate = accent_matches[0];
        if candidate.freq >= 12000 {
            return Some(candidate.exact.clone());
        }
    } else if accent_matches.len() > 1 {
        // Formas ambíguas nunca são substituídas automaticamente no espaço
        return None;
    }

    // Palavras com até 4 letras nunca sofrem correção por distância de edição
    if is_short_word {
        return None;
    }

    // 3. Verificação de proximidade/distância de edição conservadora (apenas distância = 1 e palavras >= 5 letras)
    let prev_norm = normalize(prev_word.trim());
    let mut best_candidate: Option<(String, u32)> = None;

    for u in &dict.unigrams {
        if stoplist.contains(&u.exact.to_lowercase().as_str()) {
            continue;
        }
        let dist = levenshtein(&typed_norm, &u.norm);
        if dist == 1 && u.freq >= 20000 {
            let mut score = u.freq as u32;
            if !prev_norm.is_empty() {
                if let Some(list) = dict.bigrams_by_prev.get(&prev_norm) {
                    for (next_w, w_weight) in list {
                        if next_w.to_lowercase() == u.exact.to_lowercase() {
                            score += *w_weight as u32 * 2;
                        }
                    }
                }
            }

            match &best_candidate {
                Some((_, best_score)) => {
                    if score > *best_score {
                        best_candidate = Some((u.exact.clone(), score));
                    }
                }
                None => {
                    best_candidate = Some((u.exact.clone(), score));
                }
            }
        }
    }

    best_candidate.map(|(w, _)| w)
}
