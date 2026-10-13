use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct Unigram {
    pub exact: String,
    pub norm: String,
    pub freq: u16,
}

#[derive(Debug, Clone)]
pub struct Bigram {
    pub prev_norm: String,
    pub next_exact: String,
    pub weight: u16,
}

pub struct Dictionary {
    pub unigrams: Vec<Unigram>,
    pub bigrams_by_prev: HashMap<String, Vec<(String, u16)>>,
    pub word_set: HashMap<String, u16>, // exact lowercase -> freq
}

pub fn normalize(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        let lower = c.to_lowercase().next().unwrap_or(c);
        match lower {
            'á' | 'à' | 'â' | 'ã' | 'ä' => out.push('a'),
            'é' | 'è' | 'ê' | 'ë' => out.push('e'),
            'í' | 'ì' | 'î' | 'ï' => out.push('i'),
            'ó' | 'ò' | 'ô' | 'õ' | 'ö' => out.push('o'),
            'ú' | 'ù' | 'û' | 'ü' => out.push('u'),
            'ç' => out.push('c'),
            'ñ' => out.push('n'),
            other => {
                if other.is_alphabetic() {
                    out.push(other);
                }
            }
        }
    }
    out
}

impl Dictionary {
    pub fn empty() -> Self {
        Self {
            unigrams: Vec::new(),
            bigrams_by_prev: HashMap::new(),
            word_set: HashMap::new(),
        }
    }

    pub fn from_bytes(bytes: &[u8]) -> Result<Self, &'static str> {
        if bytes.len() < 14 {
            return Err("Dados insuficientes para cabeçalho");
        }

        if &bytes[0..4] != b"PTKB" {
            return Err("Magic inválido, esperado PTKB");
        }

        let version = u16::from_le_bytes([bytes[4], bytes[5]]);
        if version != 1 {
            return Err("Versão não suportada");
        }

        let unigram_count = u32::from_le_bytes([bytes[6], bytes[7], bytes[8], bytes[9]]) as usize;
        let bigram_count = u32::from_le_bytes([bytes[10], bytes[11], bytes[12], bytes[13]]) as usize;

        let mut offset = 14;
        let mut unigrams = Vec::with_capacity(unigram_count);
        let mut word_set = HashMap::with_capacity(unigram_count);

        for _ in 0..unigram_count {
            if offset >= bytes.len() {
                return Err("Fim inesperado ao ler unigramas");
            }
            let exact_len = bytes[offset] as usize;
            offset += 1;
            if offset + exact_len > bytes.len() {
                return Err("Buffer curto para exact_word");
            }
            let exact = std::str::from_utf8(&bytes[offset..offset + exact_len])
                .map_err(|_| "UTF-8 inválido em unigrama")?
                .to_string();
            offset += exact_len;

            if offset >= bytes.len() {
                return Err("Buffer curto para norm_len");
            }
            let norm_len = bytes[offset] as usize;
            offset += 1;
            if offset + norm_len > bytes.len() {
                return Err("Buffer curto para norm_word");
            }
            let norm = std::str::from_utf8(&bytes[offset..offset + norm_len])
                .map_err(|_| "UTF-8 inválido em norm_word")?
                .to_string();
            offset += norm_len;

            if offset + 2 > bytes.len() {
                return Err("Buffer curto para freq");
            }
            let freq = u16::from_le_bytes([bytes[offset], bytes[offset + 1]]);
            offset += 2;

            word_set.insert(exact.to_lowercase(), freq);
            unigrams.push(Unigram { exact, norm, freq });
        }

        let mut bigrams_by_prev: HashMap<String, Vec<(String, u16)>> = HashMap::new();
        for _ in 0..bigram_count {
            if offset >= bytes.len() {
                return Err("Fim inesperado ao ler bigramas");
            }
            let prev_len = bytes[offset] as usize;
            offset += 1;
            if offset + prev_len > bytes.len() {
                return Err("Buffer curto para prev_norm");
            }
            let prev_norm = std::str::from_utf8(&bytes[offset..offset + prev_len])
                .map_err(|_| "UTF-8 inválido em prev_norm")?
                .to_string();
            offset += prev_len;

            if offset >= bytes.len() {
                return Err("Buffer curto para next_len");
            }
            let next_len = bytes[offset] as usize;
            offset += 1;
            if offset + next_len > bytes.len() {
                return Err("Buffer curto para next_exact");
            }
            let next_exact = std::str::from_utf8(&bytes[offset..offset + next_len])
                .map_err(|_| "UTF-8 inválido em next_exact")?
                .to_string();
            offset += next_len;

            if offset + 2 > bytes.len() {
                return Err("Buffer curto para bigram weight");
            }
            let weight = u16::from_le_bytes([bytes[offset], bytes[offset + 1]]);
            offset += 2;

            bigrams_by_prev
                .entry(prev_norm)
                .or_default()
                .push((next_exact, weight));
        }

        Ok(Self {
            unigrams,
            bigrams_by_prev,
            word_set,
        })
    }

    pub fn is_word(&self, word: &str) -> bool {
        self.word_set.contains_key(&word.to_lowercase())
    }
}
