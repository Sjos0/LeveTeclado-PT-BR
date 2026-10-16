use crate::dictionary::{normalize, Dictionary, Unigram};

/// Nomes próprios e estrangeirismos que nunca podem ser o ALVO de uma autocorreção
/// (ver stoplist_names.txt). Palavras protegidas como typed-word continuam na stoplist.
const NAMES: [&str; 5] = ["francois", "françois", "roma", "victor", "max"];

/// Posição aproximada de uma letra num teclado QWERTY: (x em quartos de tecla, linha).
fn key_position(c: char) -> Option<(i32, i32)> {
    let rows: [(&str, i32, i32); 3] = [
        ("qwertyuiop", 0, 0),
        ("asdfghjkl", 1, 1),
        ("zxcvbnm", 3, 2),
    ];
    for (letters, offset, line) in rows.iter() {
        if let Some(col) = letters.find(c) {
            return Some((col as i32 * 4 + *offset, *line));
        }
    }
    None
}

/// Duas letras são "vizinhas" se estão na mesma tecla ou em teclas encostadas no teclado.
fn are_neighbor_keys(a: char, b: char) -> bool {
    if a == b {
        return false;
    }
    match (key_position(a), key_position(b)) {
        (Some((xa, ya)), Some((xb, yb))) => (ya - yb).abs() <= 1 && (xa - xb).abs() <= 4,
        _ => false,
    }
}

/// Verdadeiro só para erros de digitação plausíveis de `candidate`:
/// - uma tecla errada, vizinha da certa (ex.: "abracp" -> "abraco");
/// - duas letras trocadas de lugar (ex.: "ontme" -> "ontem");
/// - uma letra dobrada a mais ou a menos (ex.: "muuito" -> "muito").
/// Trocar uma letra por outra não vizinha, ou acrescentar/remover uma letra que não é
/// dobrada, NÃO é erro típico de digitação: são palavras válidas diferentes
/// ("parar" -> "para", "disso" -> "isso", "muita" -> "muito"). Por isso ficam de fora.
fn is_plausible_typo(typed: &str, candidate: &str) -> bool {
    // Filtro barato por tamanho: rejeita antes de alocar os vetores de caracteres.
    if typed.chars().count().abs_diff(candidate.chars().count()) > 1 {
        return false;
    }

    let a: Vec<char> = typed.chars().collect();
    let b: Vec<char> = candidate.chars().collect();

    if a.len() == b.len() {
        let diffs: Vec<usize> = (0..a.len()).filter(|&i| a[i] != b[i]).collect();
        return match diffs.as_slice() {
            [i] => are_neighbor_keys(a[*i], b[*i]),
            [i, j] => *j == *i + 1 && a[*i] == b[*j] && a[*j] == b[*i],
            _ => false,
        };
    }

    let (longer, shorter) = if a.len() == b.len() + 1 {
        (&a, &b)
    } else if b.len() == a.len() + 1 {
        (&b, &a)
    } else {
        return false;
    };

    (0..longer.len()).any(|j| {
        let doubled = (j > 0 && longer[j] == longer[j - 1])
            || (j + 1 < longer.len() && longer[j] == longer[j + 1]);
        doubled && longer[..j].iter().chain(longer[j + 1..].iter()).eq(shorter.iter())
    })
}

/// Os 3 unigramas de maior frequência, na mesma ordem que um sort estável por frequência
/// daria (empates mantêm a ordem do dicionário). Não clona nem ordena o vetor inteiro.
fn top_unigrams(dict: &Dictionary) -> Vec<&Unigram> {
    let mut top: Vec<&Unigram> = Vec::with_capacity(4);
    for u in &dict.unigrams {
        // Primeira posição com frequência estritamente menor: o empate fica depois do existente.
        let pos = top.iter().position(|t| u.freq > t.freq).unwrap_or(top.len());
        if pos < 3 {
            top.insert(pos, u);
            top.truncate(3);
        }
    }
    top
}

pub fn get_suggestions(dict: &Dictionary, prev_word: &str, prefix: &str) -> Vec<String> {
    let clean_prefix = prefix.trim();
    let prev_norm = normalize(prev_word.trim());

    if clean_prefix.is_empty() {
        // Sugestões contextuais baseadas na palavra anterior (bigramas)
        if !prev_norm.is_empty() {
            if let Some(next_list) = dict.bigrams_by_prev.get(&prev_norm) {
                let mut sorted: Vec<&(String, u16)> = next_list.iter().collect();
                sorted.sort_by(|a, b| b.1.cmp(&a.1));
                return sorted
                    .into_iter()
                    .map(|(w, _)| w.clone())
                    .take(3)
                    .collect();
            }
        }
        // Se não houver contexto, retorna as 3 palavras mais frequentes
        return top_unigrams(dict)
            .into_iter()
            .map(|u| u.exact.clone())
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

    // Candidatos emprestam o texto do dicionário (&str); só o resultado final é clonado.
    let mut scored_candidates: Vec<(&str, u32)> = Vec::new();

    for u in &dict.unigrams {
        if u.norm.starts_with(prefix_norm.as_str()) {
            let exact_lower = u.exact.to_lowercase();
            let mut score = u.freq as u32;

            // Bônus de bigrama se a palavra anterior prevê este termo
            if let Some(&b_score) = bigram_bonuses.get(exact_lower.as_str()) {
                score += b_score;
            }

            // Bônus alto para correspondência exata desconsiderando acento (ex: 'voce' -> 'você')
            if u.norm == prefix_norm {
                score += 50_000;
            }

            // Bônus máximo se digitou exatamente igual
            if exact_lower == prefix_lower {
                score += 80_000;
            }

            scored_candidates.push((u.exact.as_str(), score));
        }
    }

    // Ordenar decrescente pelo score
    scored_candidates.sort_by(|a, b| b.1.cmp(&a.1));

    // Deduplicar mantendo a ordem
    let mut results: Vec<String> = Vec::with_capacity(3);
    for (cand, _) in scored_candidates {
        if !results.iter().any(|r| r.as_str() == cand) {
            results.push(cand.to_string());
            if results.len() == 3 {
                break;
            }
        }
    }

    results
}

pub fn get_auto_correction(dict: &Dictionary, prev_word: &str, typed_word: &str) -> Option<String> {
    let clean = typed_word.trim();
    if clean.chars().count() < 3 {
        return None;
    }

    let typed_lower = clean.to_lowercase();

    // 0. Stoplist: palavras que nunca são corrigidas (válidas fora do dicionário, gírias, abreviações)
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

    // 1. Conservador: se já for uma palavra válida exatamente no dicionário, NÃO corrige!
    if dict.is_word(&typed_lower) {
        return None;
    }

    let is_short_word = clean.chars().count() <= 4;
    let typed_norm = normalize(clean);
    // Palavra já digitada com acento (ex.: "dá", "às") não sofre correção de acento:
    // o usuário escreveu a forma certa, mesmo que ela não esteja no dicionário.
    let typed_has_accent = typed_lower != typed_norm;

    // 2. Acento faltando (ex.: 'nao' -> 'não', 'voce' -> 'você', 'mae' -> 'mãe')
    if !typed_has_accent {
        let accent_matches: Vec<&Unigram> = dict
            .unigrams
            .iter()
            .filter(|u| u.norm == typed_norm)
            .collect();

        if accent_matches.len() == 1 {
            let candidate = accent_matches[0];
            if candidate.freq >= 12000 {
                return Some(candidate.exact.clone());
            }
        } else if accent_matches.len() > 1 {
            // Formas ambíguas (ex.: 'po' -> pó/pô) nunca são substituídas automaticamente
            return None;
        }
    }

    // Palavras com até 4 letras nunca sofrem correção por distância de edição
    if is_short_word {
        return None;
    }

    // 3. Erro de digitação plausível (ver is_plausible_typo), só para palavras frequentes
    let prev_norm = normalize(prev_word.trim());
    let mut best_candidate: Option<(String, u32)> = None;

    for u in &dict.unigrams {
        // Filtros puros e baratos primeiro; a checagem de nomes aloca, então roda só para candidatos reais.
        if u.freq < 20000 || !is_plausible_typo(&typed_norm, &u.norm) {
            continue;
        }
        if NAMES.contains(&u.exact.to_lowercase().as_str()) {
            continue;
        }

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
            Some((_, best_score)) if score <= *best_score => {}
            _ => best_candidate = Some((u.exact.clone(), score)),
        }
    }

    best_candidate.map(|(w, _)| w)
}
