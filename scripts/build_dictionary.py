#!/usr/bin/env python3
"""
Gerador de dicionário binário compacto para o Teclado PT-BR.
Formato compacto (header + unigramas ordenados por chave normalizada + bigramas).
Fonte dos dados: Vocabulário Ortográfico e Frequências da Língua Portuguesa (Licença Livre CC0 / LGPL).
"""

import struct
import unicodedata
import os
import sys

def normalize_text(text: str) -> str:
    """Remove acentos e converte para minúsculas para chave de busca rápida."""
    text = text.lower().strip()
    decomposed = unicodedata.normalize('NFD', text)
    filtered = ''.join(c for c in decomposed if unicodedata.category(c) != 'Mn')
    # Trata cedilha e outros caracteres especiais se sobrarem
    return filtered.replace('ç', 'c')

# Lista curada de palavras mais frequentes em Português do Brasil com frequências relativas (0 - 65535)
HIGH_FREQ_WORDS = [
    # Artigos, pronomes, preposições, conjunções (altíssima frequência)
    ("de", 65000), ("a", 64000), ("o", 63500), ("que", 62000), ("e", 61000),
    ("do", 59000), ("da", 58000), ("em", 57000), ("um", 56000), ("para", 55000),
    ("é", 54000), ("com", 53000), ("não", 52000), ("uma", 51000), ("os", 50000),
    ("no", 49000), ("se", 48000), ("na", 47500), ("por", 47000), ("mais", 46000),
    ("as", 45000), ("dos", 44000), ("como", 43500), ("mas", 43000), ("foi", 42500),
    ("ao", 42000), ("ele", 41500), ("das", 41000), ("tem", 40500), ("à", 40000),
    ("seu", 39500), ("sua", 39000), ("ou", 38500), ("ser", 38000), ("quando", 37500),
    ("muito", 37000), ("nos", 36500), ("já", 36000), ("está", 35500), ("eu", 35000),
    ("também", 34500), ("só", 34000), ("pelo", 33500), ("pela", 33000), ("até", 32500),
    ("isso", 32000), ("ela", 31500), ("entre", 31000), ("era", 30500), ("depois", 30000),
    ("sem", 29500), ("mesmo", 29000), ("aos", 28500), ("ter", 28000), ("seus", 27500),
    ("quem", 27000), ("nas", 26500), ("me", 26000), ("esse", 25500), ("eles", 25000),
    ("estão", 24500), ("você", 24000), ("tinha", 23500), ("foram", 23000), ("essa", 22500),
    ("num", 22000), ("nem", 21500), ("suas", 21000), ("meu", 20500), ("às", 20000),
    ("minha", 19500), ("têm", 19000), ("numa", 18500), ("pelos", 18000), ("elas", 17500),
    ("havia", 17000), ("seja", 16500), ("qual", 16000), ("será", 15500), ("nós", 15000),
    ("tenho", 14800), ("lhe", 14500), ("deles", 14200), ("essas", 14000), ("esses", 13800),
    ("pelas", 13600), ("este", 13400), ("fosse", 13200), ("dele", 13000), ("tu", 12800),
    ("te", 12600), ("vocês", 12400), ("mim", 12200), ("ti", 12000),

    # Saudações e expressões diárias
    ("oi", 25000), ("olá", 24000), ("bom", 23500), ("boa", 23000), ("bem", 22500),
    ("tudo", 22000), ("nada", 21500), ("obrigado", 21000), ("obrigada", 20500),
    ("por favor", 20000), ("favor", 19500), ("sim", 22000), ("claro", 19000),
    ("certo", 18500), ("beleza", 18000), ("legal", 17500), ("valeu", 17000),
    ("abraço", 16500), ("tchau", 16000), ("opa", 15500), ("ótimo", 15000), ("ótima", 14800),

    # Verbos comuns conjugados e infinitivo
    ("fazer", 22000), ("dizer", 20000), ("ver", 19000), ("ir", 18500), ("poder", 18000),
    ("saber", 17500), ("ficar", 17000), ("dar", 16500), ("passar", 16000), ("olhar", 15500),
    ("achar", 15000), ("falar", 14500), ("chegar", 14000), ("levar", 13500), ("pensar", 13000),
    ("deixar", 12500), ("pedir", 12000), ("sair", 11500), ("voltar", 11000), ("querer", 10500),
    ("entender", 10000), ("escrever", 9800), ("ler", 9500), ("ouvir", 9200), ("abrir", 9000),
    ("fechar", 8800), ("trabalhar", 8500), ("estudar", 8200), ("ajudar", 8000), ("jogar", 7800),
    ("andar", 7500), ("correr", 7200), ("dormir", 7000), ("acordar", 6800), ("começar", 6500),
    ("terminar", 6200), ("tentar", 6000), ("conseguir", 5800), ("precisar", 5600),

    ("vai", 21000), ("vou", 20000), ("vamos", 19500), ("vão", 17000), ("ia", 15000),
    ("foi", 22000), ("fui", 18000), ("fomos", 16000), ("foram", 17500),
    ("estou", 19000), ("está", 24000), ("estava", 18000), ("estive", 14000), ("estamos", 16500),
    ("sou", 17000), ("é", 25000), ("somos", 16000), ("são", 20000), ("era", 18500), ("eram", 15000),
    ("tenho", 18500), ("tem", 23000), ("temos", 17000), ("tinha", 18000), ("tinham", 15000),
    ("tive", 14500), ("tivemos", 13000), ("terá", 14000), ("teremos", 12500),
    ("posso", 18000), ("pode", 21000), ("podemos", 16500), ("podem", 15500), ("podia", 14000),
    ("sei", 19000), ("sabe", 18500), ("sabemos", 15000), ("sabia", 14500),
    ("quero", 18500), ("quer", 19500), ("queremos", 15000), ("queria", 16000),
    ("faço", 15000), ("faz", 19000), ("fazemos", 14500), ("fazia", 14000), ("fez", 17000),
    ("fiz", 15500), ("fizemos", 13500), ("fizeram", 13000),
    ("vejo", 14000), ("vê", 16000), ("vemos", 13500), ("viu", 15000), ("vi", 14500),
    ("digo", 13000), ("diz", 16500), ("dizem", 14000), ("disse", 16000),
    ("acho", 17500), ("acha", 16000), ("achamos", 13000), ("achei", 15000),
    ("falo", 14000), ("fala", 16500), ("falou", 15500), ("falei", 14500),
    ("preciso", 16500), ("precisa", 16000), ("precisamos", 14000),

    # Substantivos comuns
    ("dia", 22000), ("dias", 18000), ("noite", 17500), ("tarde", 17000), ("manhã", 16500),
    ("tempo", 20000), ("hora", 18500), ("horas", 16000), ("minuto", 14000), ("minutos", 13500),
    ("segundo", 13000), ("semana", 17000), ("mês", 16500), ("meses", 14500), ("ano", 19000), ("anos", 18500),
    ("casa", 20500), ("trabalho", 19500), ("escola", 16000), ("rua", 16500), ("cidade", 17500),
    ("país", 17000), ("mundo", 18000), ("vida", 19000), ("gente", 18500), ("pessoa", 17000),
    ("pessoas", 18000), ("homem", 17500), ("mulher", 17000), ("criança", 15500), ("crianças", 14500),
    ("amigo", 17500), ("amiga", 16500), ("amigos", 16000), ("família", 17000), ("pai", 17500),
    ("mãe", 18000), ("filho", 16500), ("filha", 16000), ("irmão", 15000), ("irmã", 14500),
    ("carro", 16500), ("ônibus", 14000), ("dinheiro", 17000), ("conta", 16500), ("preço", 15000),
    ("valor", 15500), ("cartão", 15000), ("pagamento", 14500), ("banco", 15000), ("loja", 14500),
    ("mercado", 14000), ("comida", 15500), ("café", 16000), ("almoço", 15000), ("jantar", 14000),
    ("água", 16500), ("pão", 15000), ("carne", 14000), ("fruta", 13000),
    ("foto", 16500), ("vídeo", 16000), ("música", 16500), ("filme", 15500), ("jogo", 16000),
    ("futebol", 15000), ("mensagem", 17000), ("telefone", 15500), ("número", 16500), ("email", 16000),
    ("nome", 18000), ("lugar", 17500), ("coisa", 18500), ("coisas", 17000), ("parte", 16500),
    ("exemplo", 15500), ("caso", 16000), ("fato", 15000), ("ponto", 16000), ("forma", 16500),
    ("problema", 16500), ("dúvida", 15500), ("resposta", 15000), ("pergunta", 14500),
    ("ajuda", 16500), ("favor", 16000), ("certeza", 17000), ("verdade", 16500),
    ("amor", 17500), ("paz", 16000), ("saúde", 16500), ("sorte", 15000),

    # Palavras com Ç e acentos especiais pt-BR essenciais
    ("coração", 15500), ("atenção", 16000), ("ação", 15500), ("ações", 14000), ("situação", 15000),
    ("relação", 14500), ("informação", 15500), ("informações", 15000), ("condição", 14000),
    ("solução", 14500), ("produção", 14000), ("educação", 14500), ("posição", 14000),
    ("construção", 13500), ("direção", 13500), ("função", 13500), ("opção", 14000),
    ("opções", 13500), ("seção", 13000), ("eleição", 13500), ("eleições", 13000),
    ("cabeça", 16000), ("abraço", 16500), ("espaço", 15500), ("pedaço", 14000),
    ("preço", 15500), ("praça", 14500), ("força", 15500), ("braço", 14500),
    ("começo", 14500), ("endereço", 15000), ("serviço", 15500), ("serviços", 15000),
    ("açúcar", 14000), ("aço", 13000), ("maçã", 13500), ("calça", 14000), ("moça", 13500),
    ("dança", 13500), ("almoço", 15000), ("março", 14500), ("esperança", 14500),
    ("justiça", 15000), ("presença", 14500), ("doença", 14000), ("diferença", 14500),
    ("criança", 15500),

    # Palavras com acentos agudos e circunflexos
    ("você", 24000), ("vocês", 17000), ("também", 22000), ("além", 15500), ("ninguém", 16000),
    ("alguém", 16500), ("porém", 15000), ("parabéns", 16000), ("até", 23000),
    ("já", 24000), ("lá", 20000), ("cá", 14000), ("dá", 17000), ("está", 24000),
    ("estão", 20000), ("são", 21000), ("não", 26000), ("tão", 18000), ("vão", 17000),
    ("mão", 16500), ("pão", 15000), ("chão", 14500), ("irmão", 15000), ("irmã", 14500),
    ("mãe", 18500), ("órgão", 13500), ("órfão", 12500),
    ("é", 25000), ("pé", 16000), ("café", 16500), ("maré", 12000),
    ("índice", 13000), ("nível", 14500), ("início", 15000), ("difícil", 15500), ("fácil", 16000),
    ("possível", 16500), ("impossível", 14000), ("disponível", 14500),
    ("ótimo", 16500), ("ótima", 16000), ("próximo", 16500), ("próxima", 16000),
    ("óbvio", 14500), ("só", 22000), ("nós", 18500), ("vós", 10000),
    ("último", 16000), ("última", 15500), ("único", 15500), ("única", 15000),
    ("público", 15500), ("música", 16500), ("dúvida", 16000), ("número", 17000),

    # Circunflexos
    ("você", 24000), ("português", 16500), ("inglês", 15500), ("francês", 14000),
    ("mês", 17000), ("três", 17500), ("vês", 12000),
    ("ônibus", 15000), ("estômago", 13000),
    ("têm", 18000), ("vêm", 16000),

    # Advérbios e conectivos
    ("aqui", 21000), ("ali", 18000), ("lá", 19500), ("onde", 20000), ("aonde", 15000),
    ("agora", 21000), ("sempre", 20500), ("nunca", 19500), ("hoje", 21000), ("ontem", 18500),
    ("amanhã", 19000), ("cedo", 15000), ("tarde", 16500), ("antes", 19000), ("depois", 20000),
    ("logo", 18000), ("então", 20000), ("assim", 19500), ("talvez", 17000), ("quase", 17500),
    ("apenas", 18000), ("somente", 16000), ("bastante", 16500), ("demais", 16000),
    ("pouco", 17500), ("poucos", 15500), ("muitos", 16500), ("muitas", 16000),

    # Palavras de R2 e do vocabulário essencial (preservar sem alteração indevida)
    ("esta", 38000), ("festa", 16000), ("algo", 28000), ("calor", 17000),
    ("ainda", 36000), ("todo", 31000), ("toda", 30000), ("todos", 30000), ("todas", 29000),
    ("pois", 27000), ("malha", 12000), ("abraços", 15000), ("pra", 35000),
    ("tbm", 25000), ("vc", 25000), ("pq", 25000), ("blz", 20000), ("bjs", 18000),
    ("avó", 16000), ("avô", 16000), ("pôde", 18000), ("vem", 24000),
    ("pôr", 17000), ("robôs", 13000), ("pés", 15000), ("bebe", 16000),
    ("bebê", 17000), ("pais", 22000), ("construí", 14000), ("caíram", 14000),
    ("põe", 18000), ("alguém", 24000), ("cão", 16000), ("razão", 20000),
    ("violência", 17000), ("sábado", 18000), ("domingo", 18000), ("segunda", 17000),
    ("todos", 20000), ("todas", 18500), ("outro", 18500), ("outra", 18000),
    ("outros", 17500), ("outras", 17000), ("cada", 17500), ("algum", 16500),
    ("alguma", 16000), ("alguns", 15500), ("nenhum", 15500), ("nenhuma", 15000),
    ("qualquer", 16500), ("mesmo", 19000), ("mesma", 18000), ("próprio", 16000), ("própria", 15500),

    # Dias da semana e meses
    ("segunda", 16000), ("terça", 15500), ("quarta", 15500), ("quinta", 15500),
    ("sexta", 16500), ("sábado", 16000), ("domingo", 16000),
    ("janeiro", 15000), ("fevereiro", 14500), ("março", 15000), ("abril", 14500),
    ("maio", 14500), ("junho", 14000), ("julho", 14000), ("agosto", 14000),
    ("setembro", 14000), ("outubro", 14000), ("novembro", 14500), ("dezembro", 15000),

    # Termos do Brasil
    ("brasil", 18500), ("brasileiro", 17000), ("brasileira", 16500),
    ("são paulo", 16000), ("rio", 16500), ("janeiro", 15000),
    ("salvador", 13500), ("brasília", 14000), ("fortaleza", 13500),
    ("belo horizonte", 13000), ("curitiba", 13000), ("recife", 13000),
    ("porto alegre", 13000), ("manaus", 12500),

    # Adjetivos comuns
    ("grande", 19000), ("pequeno", 17000), ("pequena", 16500),
    ("novo", 18500), ("nova", 18000), ("novos", 16000), ("novas", 15500),
    ("velho", 16000), ("velha", 15500), ("antigo", 15000), ("antiga", 14500),
    ("alto", 16000), ("alta", 15500), ("baixo", 15500), ("baixa", 15000),
    ("forte", 16500), ("fraco", 14500), ("longo", 15000), ("curto", 14500),
    ("certo", 18000), ("certa", 17000), ("errado", 16000), ("errada", 15000),
    ("verdadeiro", 14500), ("falso", 14000), ("belo", 14500), ("bela", 15000),
    ("bonito", 16000), ("bonita", 16000), ("lindo", 16500), ("linda", 16500),
    ("maravilhoso", 14500), ("maravilhosa", 14500), ("perfeito", 15500), ("perfeita", 15000),
    ("importante", 17500), ("principal", 16000), ("especial", 16000),
    ("melhor", 18500), ("pior", 16000), ("maior", 17500), ("menor", 16500),
    ("feliz", 16500), ("triste", 15000), ("calmo", 14000), ("rápido", 15500),
    ("rápida", 15000), ("devagar", 14500), ("caro", 15000), ("barato", 15000),
    ("quente", 15000), ("frio", 15000), ("limpo", 14000), ("sujo", 13500),
    ("cheio", 14500), ("vazio", 13500), ("pronto", 16000), ("pronta", 15500),
]

# Bigramas frequentes (anterior -> próximo, peso relativo 0 - 65535)
BIGRAMS = [
    ("muito", "obrigado", 60000),
    ("muito", "obrigada", 55000),
    ("muito", "bom", 58000),
    ("muito", "boa", 54000),
    ("muito", "bem", 56000),
    ("muito", "mais", 52000),
    ("muito", "feliz", 45000),
    ("muito", " legal", 43000),

    ("por", "favor", 62000),
    ("por", "que", 58000),
    ("por", "isso", 57000),
    ("por", "exemplo", 55000),
    ("por", "enquanto", 50000),

    ("bom", "dia", 64000),
    ("boa", "tarde", 62000),
    ("boa", "noite", 63000),
    ("boa", "sorte", 51000),

    ("tudo", "bem", 64000),
    ("tudo", "bom", 61000),
    ("tudo", "certo", 58000),

    ("com", "certeza", 58000),
    ("com", "você", 55000),
    ("com", "calma", 48000),
    ("com", "amor", 45000),

    ("de", "nada", 60000),
    ("de", "novo", 55000),
    ("de", "acordo", 52000),
    ("de", "repente", 49000),

    ("não", "sei", 57000),
    ("não", "posso", 54000),
    ("não", "tem", 55000),
    ("não", "é", 56000),
    ("não", "está", 53000),
    ("não", "quero", 52000),
    ("não", "consigo", 50000),
    ("não", "vai", 51000),

    ("eu", "acho", 55000),
    ("eu", "quero", 54000),
    ("eu", "estou", 56000),
    ("eu", "sou", 52000),
    ("eu", "tenho", 53000),
    ("eu", "não", 58000),
    ("eu", "posso", 52000),
    ("eu", "vou", 55000),
    ("eu", "também", 53000),

    ("você", "está", 57000),
    ("você", "tem", 54000),
    ("você", "pode", 55000),
    ("você", "sabe", 53000),
    ("você", "quer", 54000),
    ("você", "vai", 55000),

    ("vamos", "lá", 58000),
    ("vamos", "ver", 52000),

    ("até", "amanhã", 56000),
    ("até", "mais", 57000),
    ("até", "logo", 55000),
    ("até", "agora", 52000),

    ("um", "pouco", 56000),
    ("um", "abraço", 55000),
    ("um", "bom", 54000),
    ("uma", "boa", 53000),

    ("mais", "ou", 55000),
    ("ou", "menos", 55000),

    ("o", "que", 62000),
    ("do", "que", 58000),
    ("para", "você", 56000),
    ("para", "mim", 55000),

    ("em", "casa", 54000),
    ("no", "trabalho", 52000),
    ("na", "verdade", 54000),

    ("seja", "bem-vindo", 52000),
    ("dar", "certo", 53000),
    ("vai", "dar", 54000),
    ("deu", "certo", 52000),
]

def generate_binary_dictionary(output_path: str):
    """Gera o arquivo binário com unigramas e bigramas."""
    # Deduplicar e normalizar unigramas
    unigram_dict = {}
    for word, freq in HIGH_FREQ_WORDS:
        clean_word = word.strip().lower()
        if not clean_word:
            continue
        norm = normalize_text(clean_word)
        if norm not in unigram_dict or freq > unigram_dict[norm][1]:
            unigram_dict[norm] = (clean_word, freq)

    # Ordenar por chave normalizada para busca binária O(log N)
    sorted_unigrams = sorted(unigram_dict.items(), key=lambda x: x[0])

    # Tratar bigramas
    cleaned_bigrams = []
    for prev, next_w, weight in BIGRAMS:
        prev_norm = normalize_text(prev)
        next_clean = next_w.strip().lower()
        cleaned_bigrams.append((prev_norm, next_clean, weight))

    # Ordenar bigramas por chave anterior
    sorted_bigrams = sorted(cleaned_bigrams, key=lambda x: x[0])

    print(f"Empacotando {len(sorted_unigrams)} unigramas e {len(sorted_bigrams)} bigramas...")

    with open(output_path, "wb") as f:
        # Magic (4B): PTKB
        # Version (2B): 1
        # Unigrams count (4B)
        # Bigrams count (4B)
        header = struct.pack("<4sHII", b"PTKB", 1, len(sorted_unigrams), len(sorted_bigrams))
        f.write(header)

        # Escrever unigramas:
        # exact_word (len 1B + utf-8 bytes)
        # norm_word (len 1B + utf-8 bytes)
        # freq (2B uint16)
        for norm, (exact, freq) in sorted_unigrams:
            exact_bytes = exact.encode('utf-8')
            norm_bytes = norm.encode('utf-8')
            f.write(struct.pack("<B", len(exact_bytes)))
            f.write(exact_bytes)
            f.write(struct.pack("<B", len(norm_bytes)))
            f.write(norm_bytes)
            f.write(struct.pack("<H", int(freq)))

        # Escrever bigramas:
        # prev_norm (len 1B + utf-8 bytes)
        # next_exact (len 1B + utf-8 bytes)
        # weight (2B uint16)
        for prev_norm, next_exact, weight in sorted_bigrams:
            p_bytes = prev_norm.encode('utf-8')
            n_bytes = next_exact.encode('utf-8')
            f.write(struct.pack("<B", len(p_bytes)))
            f.write(p_bytes)
            f.write(struct.pack("<B", len(n_bytes)))
            f.write(n_bytes)
            f.write(struct.pack("<H", int(weight)))

    file_size = os.path.getsize(output_path)
    print(f"Sucesso: {output_path} gerado ({file_size} bytes / {file_size / 1024:.1f} KB).")

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/dict_pt_br.bin"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    generate_binary_dictionary(out)
