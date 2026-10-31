import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Xadrez no terminal: dois jogadores ou contra o Ollama.
// Tudo num arquivo so, sem biblioteca externa. Roda com: java Xadrez.java
public class Xadrez {

    // Direcoes que torre/dama e bispo/dama usam pra andar pelo tabuleiro.
    private static final int[][] ORTOGONAIS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAGONAIS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] SALTOS_CAVALO = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };
    private static final int VALOR_MATE = 1_000_000;

    public static void main(String[] args) {
        // Abre o teclado e entrega o controle pro jogo.
        try (Scanner scanner = new Scanner(System.in)) {
            new Jogo(scanner).iniciar();
        }
    }

    // Pega as jogadas de cada peca e joga fora as que deixariam o proprio rei em xeque.
    private static List<Movimento> movimentosLegais(Posicao posicao) {
        List<Movimento> legais = new ArrayList<>();
        boolean brancas = posicao.brancasJogam;
        for (int linha = 0; linha < 8; linha++) {
            for (int coluna = 0; coluna < 8; coluna++) {
                char peca = posicao.tabuleiro[linha][coluna];
                if (!Posicao.daCor(peca, brancas)) {
                    continue;
                }
                for (Movimento movimento : posicao.gerarPseudo(linha, coluna)) {
                    Posicao proxima = posicao.aplicar(movimento);
                    if (!proxima.emXeque(brancas)) {
                        legais.add(movimento);
                    }
                }
            }
        }
        return legais;
    }

    // Nota simples: material + um tiquinho de bonus pra quem domina o centro.
    private static int avaliar(Posicao posicao, boolean paraBrancas) {
        int nota = 0;
        for (int linha = 0; linha < 8; linha++) {
            for (int coluna = 0; coluna < 8; coluna++) {
                char peca = posicao.tabuleiro[linha][coluna];
                if (peca == '.') {
                    continue;
                }
                int valor = switch (Character.toLowerCase(peca)) {
                    case 'p' -> 100;
                    case 'c' -> 320;
                    case 'b' -> 330;
                    case 't' -> 500;
                    case 'd' -> 900;
                    case 'r' -> 20000;
                    default -> 0;
                };
                int bonusCentro = 0;
                if (peca != 'r' && peca != 'R') {
                    if (coluna == 3 || coluna == 4) {
                        bonusCentro += 8;
                    }
                    if (linha == 3 || linha == 4) {
                        bonusCentro += 8;
                    }
                }
                if (Posicao.daCor(peca, paraBrancas)) {
                    nota += valor + bonusCentro;
                } else {
                    nota -= valor + bonusCentro;
                }
            }
        }
        return nota;
    }

    // Minimax com poda alpha-beta. Bem raso, so pra nao entregar peca de graca no dificil.
    private static int minimax(Posicao posicao, int profundidade, boolean iaBrancas, int alpha, int beta) {
        if (profundidade == 0) {
            return avaliar(posicao, iaBrancas);
        }
        List<Movimento> legais = movimentosLegais(posicao);
        if (legais.isEmpty()) {
            if (posicao.emXeque(posicao.brancasJogam)) {
                return posicao.brancasJogam == iaBrancas ? -VALOR_MATE - profundidade : VALOR_MATE + profundidade;
            }
            return 0;
        }
        if (posicao.brancasJogam == iaBrancas) {
            int melhor = Integer.MIN_VALUE;
            for (Movimento movimento : legais) {
                melhor = Math.max(melhor,
                        minimax(posicao.aplicar(movimento), profundidade - 1, iaBrancas, alpha, beta));
                alpha = Math.max(alpha, melhor);
                if (alpha >= beta) {
                    break;
                }
            }
            return melhor;
        }
        int melhor = Integer.MAX_VALUE;
        for (Movimento movimento : legais) {
            melhor = Math.min(melhor,
                    minimax(posicao.aplicar(movimento), profundidade - 1, iaBrancas, alpha, beta));
            beta = Math.min(beta, melhor);
            if (alpha >= beta) {
                break;
            }
        }
        return melhor;
    }

    // Cores ANSI. Se o terminal nao parecer suportar, o jogo roda normal, sem codigo estranho.
    private static final class Cores {

        private static final boolean ATIVO = decidir();
        private static final String RESET = "\u001B[0m";

        static String cor(String codigo, String texto) {
            return ATIVO ? "\u001B[" + codigo + "m" + texto + RESET : texto;
        }

        static String titulo(String texto) {
            return cor("1;36", texto);
        }

        static String borda(String texto) {
            return cor("36", texto);
        }

        static String branca(char peca) {
            return cor("1;97", String.valueOf(peca));
        }

        static String preta(char peca) {
            return cor("1;31", String.valueOf(peca));
        }

        static String erro(String texto) {
            return cor("1;31", texto);
        }

        static String sucesso(String texto) {
            return cor("1;32", texto);
        }

        static String aviso(String texto) {
            return cor("1;33", texto);
        }

        static String fraco(String texto) {
            return cor("90", texto);
        }

        static String destaque(String texto) {
            return cor("1;35", texto);
        }

        private static boolean decidir() {
            String forcado = System.getenv("XADREZ_COR");
            if (forcado != null) {
                return !forcado.equals("0");
            }
            return System.getenv("WT_SESSION") != null
                    || System.getenv("TERM") != null
                    || System.getenv("TERM_PROGRAM") != null
                    || System.getenv("COLORTERM") != null;
        }
    }

    private static final class Jogo {

        private final Scanner scanner;
        private final Random random = new Random();
        private final Ollama ollama = new Ollama();
        private Posicao posicao = Posicao.inicial();
        private Movimento ultimaJogada;
        private boolean encerrar;
        private boolean contraOllama;
        private boolean ollamaBrancas;
        private int dificuldade = 2;

        Jogo(Scanner scanner) {
            this.scanner = scanner;
        }

        void iniciar() {
            banner();
            menuInicial();
            if (encerrar) {
                System.out.println(Cores.titulo("Ate a proxima!"));
                return;
            }

            // Cada volta aqui e uma partida nova, com as mesmas configuracoes.
            while (true) {
                posicao = Posicao.inicial();
                ultimaJogada = null;
                jogarPartida();
                if (encerrar) {
                    break;
                }
                if (!confirmar(Cores.aviso("Jogar novamente? (S/N): "))) {
                    break;
                }
            }
            System.out.println();
            System.out.println(Cores.titulo("Ate a proxima!"));
        }

        private void banner() {
            System.out.println();
            System.out.println(Cores.titulo("========================================"));
            System.out.println(Cores.titulo("              X A D R E Z               "));
            System.out.println(Cores.titulo("========================================"));
            System.out.println(Cores.fraco("P=peao T=torre C=cavalo B=bispo D=dama R=rei"));
            System.out.println(Cores.fraco("Brancas: MAIUSCULAS | Pretas: minusculas"));
            System.out.println(Cores.fraco("Jogada: origem e destino (ex.: e2 e4)"));
            System.out.println(Cores.fraco("Comandos: ajuda, sair"));
        }

        private void menuInicial() {
            System.out.println();
            System.out.println("  1 - " + Cores.destaque("Jogador contra jogador"));
            System.out.println("  2 - " + Cores.destaque("Jogar contra o Ollama"));
            System.out.println("  0 - " + Cores.fraco("Sair"));

            int opcao = lerOpcao(Cores.aviso("Escolha uma opcao: "), 0, 2);
            if (encerrar || opcao == 0) {
                encerrar = true;
                return;
            }
            if (opcao == 1) {
                contraOllama = false;
                return;
            }

            if (!ollama.disponivel()) {
                System.out.println(Cores.erro("Ollama nao encontrado em " + ollama.url() + "."));
                System.out.println(Cores.aviso("Rode 'ollama serve' e abra o jogo de novo. Vamos de 1x1 por enquanto."));
                contraOllama = false;
                return;
            }

            contraOllama = true;
            System.out.println();
            System.out.println("  Nivel de dificuldade:");
            System.out.println("  1 - " + Cores.sucesso("Facil") + Cores.fraco("   (joga mais solto)"));
            System.out.println("  2 - " + Cores.aviso("Medio") + Cores.fraco("   (equilibrado)"));
            System.out.println("  3 - " + Cores.erro("Dificil") + Cores.fraco(" (nao entrega peca facil)"));
            dificuldade = lerOpcao(Cores.aviso("Escolha o nivel: "), 1, 3);
            if (encerrar) {
                return;
            }

            boolean jogadorBrancas = confirmar(Cores.aviso("Voce joga de brancas? (S/N): "));
            if (encerrar) {
                return;
            }
            ollamaBrancas = !jogadorBrancas;
            System.out.println(Cores.sucesso("Ollama de " + (ollamaBrancas ? "brancas" : "pretas")
                    + " | nivel " + nomeDificuldade() + " | modelo " + ollama.modelo()));
        }

        private void jogarPartida() {
            while (true) {
                mostrarTabuleiro();
                boolean brancas = posicao.brancasJogam;
                List<Movimento> legais = movimentosLegais(posicao);

                if (legais.isEmpty()) {
                    if (posicao.emXeque(brancas)) {
                        System.out.println(Cores.sucesso("Xeque-mate! Vitoria das "
                                + (brancas ? "pretas" : "brancas") + "."));
                    } else {
                        System.out.println(Cores.aviso("Empate por afogamento (rei sem movimentos)."));
                    }
                    return;
                }
                if (posicao.meioMovimentos >= 100) {
                    System.out.println(Cores.aviso("Empate pela regra dos 50 movimentos."));
                    return;
                }
                if (materialInsuficiente()) {
                    System.out.println(Cores.aviso("Empate por material insuficiente."));
                    return;
                }

                System.out.println();
                if (ultimaJogada != null) {
                    System.out.println(Cores.fraco("Ultima jogada: " + ultimaJogada.notacao()));
                }
                System.out.println(Cores.destaque("Vez das " + (brancas ? "brancas" : "pretas")));
                if (posicao.emXeque(brancas)) {
                    System.out.println(Cores.erro("XEQUE!"));
                }
                if (contraOllama) {
                    System.out.println(Cores.fraco("Ollama: " + nomeDificuldade() + " | " + ollama.modelo()));
                }

                boolean vezDoOllama = contraOllama && brancas == ollamaBrancas;
                Movimento jogada;
                if (vezDoOllama) {
                    System.out.println(Cores.destaque("Ollama pensando..."));
                    jogada = jogadaDoOllama(legais);
                } else {
                    jogada = lerJogada(legais);
                }
                if (jogada == null) {
                    return;
                }
                posicao = posicao.aplicar(jogada);
                ultimaJogada = jogada;
                System.out.println();
                System.out.println(Cores.sucesso((vezDoOllama ? "Ollama jogou: " : "Sua jogada: ")
                        + jogada.notacao()));
            }
        }

        private void mostrarTabuleiro() {
            String linhaBorda = Cores.borda("  +---+---+---+---+---+---+---+---+");
            System.out.println();
            System.out.println(Cores.fraco("    a   b   c   d   e   f   g   h"));
            System.out.println(linhaBorda);
            for (int linha = 0; linha < 8; linha++) {
                System.out.print(Cores.fraco(" " + (8 - linha) + " "));
                System.out.print(Cores.borda("|"));
                for (int coluna = 0; coluna < 8; coluna++) {
                    char peca = posicao.tabuleiro[linha][coluna];
                    String desenho;
                    if (peca == '.') {
                        desenho = " ";
                    } else if (Posicao.ehBranca(peca)) {
                        desenho = Cores.branca(peca);
                    } else {
                        desenho = Cores.preta(peca);
                    }
                    System.out.print(" " + desenho + " " + Cores.borda("|"));
                }
                System.out.println(Cores.fraco(" " + (8 - linha)));
                System.out.println(linhaBorda);
            }
            System.out.println(Cores.fraco("    a   b   c   d   e   f   g   h"));
        }

        private Movimento lerJogada(List<Movimento> legais) {
            while (true) {
                System.out.print(Cores.aviso("Sua jogada: "));
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    encerrar = true;
                    return null;
                }
                // Aceita "e2e4", "e2 e4" e "e2-e4" porque ninguem merece decorar um formato.
                String entrada = scanner.nextLine().trim().toLowerCase();
                if (entrada.isEmpty()) {
                    continue;
                }
                if (entrada.equals("sair")) {
                    System.out.println(Cores.fraco("Jogo encerrado."));
                    encerrar = true;
                    return null;
                }
                if (entrada.equals("ajuda")) {
                    ajuda();
                    continue;
                }
                if (!entrada.matches("[a-h][1-8][ -]?[a-h][1-8]")) {
                    System.out.println(Cores.erro("Formato invalido. Use, por exemplo, e2 e4."));
                    continue;
                }

                int colunaOrigem = entrada.charAt(0) - 'a';
                int linhaOrigem = 8 - (entrada.charAt(1) - '0');
                int deslocamento = (entrada.charAt(2) == ' ' || entrada.charAt(2) == '-') ? 1 : 0;
                int colunaDestino = entrada.charAt(2 + deslocamento) - 'a';
                int linhaDestino = 8 - (entrada.charAt(3 + deslocamento) - '0');

                List<Movimento> candidatos = new ArrayList<>();
                for (Movimento movimento : legais) {
                    if (movimento.l1 == linhaOrigem && movimento.c1 == colunaOrigem
                            && movimento.l2 == linhaDestino && movimento.c2 == colunaDestino) {
                        candidatos.add(movimento);
                    }
                }

                if (candidatos.isEmpty()) {
                    if (pseudoLegalExiste(linhaOrigem, colunaOrigem, linhaDestino, colunaDestino)) {
                        System.out.println(Cores.erro("Movimento ilegal: seu rei ficaria em xeque."));
                    } else {
                        System.out.println(Cores.erro("Movimento invalido."));
                    }
                    continue;
                }
                if (candidatos.size() > 1) {
                    return escolherPromocao(candidatos);
                }
                return candidatos.get(0);
            }
        }

        private boolean pseudoLegalExiste(int l1, int c1, int l2, int c2) {
            char peca = posicao.tabuleiro[l1][c1];
            if (!Posicao.daCor(peca, posicao.brancasJogam)) {
                return false;
            }
            for (Movimento movimento : posicao.gerarPseudo(l1, c1)) {
                if (movimento.l1 == l1 && movimento.c1 == c1
                        && movimento.l2 == l2 && movimento.c2 == c2) {
                    return true;
                }
            }
            return false;
        }

        private Movimento escolherPromocao(List<Movimento> opcoes) {
            // Peao chegou na ultima linha: escolhe no que ele vira.
            while (true) {
                System.out.print(Cores.aviso("Promover para (D/T/B/C): "));
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    encerrar = true;
                    return null;
                }
                String escolha = scanner.nextLine().trim().toUpperCase();
                char peca = switch (escolha) {
                    case "D", "Q" -> 'D';
                    case "T", "R" -> 'T';
                    case "B" -> 'B';
                    case "C", "N" -> 'C';
                    default -> 0;
                };
                if (peca == 0) {
                    System.out.println(Cores.erro("Opcao invalida."));
                    continue;
                }
                for (Movimento opcao : opcoes) {
                    if (Character.toUpperCase(opcao.promocao) == peca) {
                        return opcao;
                    }
                }
            }
        }

        private void ajuda() {
            System.out.println();
            System.out.println(Cores.titulo("+-- AJUDA -------------------------------"));
            System.out.println("| Objetivo: dar xeque-mate no rei inimigo.");
            System.out.println("| Jogada: origem e destino, ex.: e2 e4.");
            System.out.println("| Roque: mova o rei duas casas (e1 g1 ou e1 c1).");
            System.out.println("| En passant: mova o peao pra casa de captura.");
            System.out.println("| Promocao: escolha D, T, B ou C.");
            System.out.println("| Comandos: ajuda, sair.");
            System.out.println(Cores.titulo("+----------------------------------------"));
            System.out.println();
        }

        private boolean materialInsuficiente() {
            // Rei sozinho contra rei (ou com um bispo/cavalo) nao da mate.
            int menores = 0;
            for (int linha = 0; linha < 8; linha++) {
                for (int coluna = 0; coluna < 8; coluna++) {
                    char peca = posicao.tabuleiro[linha][coluna];
                    if (peca == '.') {
                        continue;
                    }
                    char tipo = Character.toLowerCase(peca);
                    if (tipo == 'p' || tipo == 't' || tipo == 'd') {
                        return false;
                    }
                    if (tipo == 'b' || tipo == 'c') {
                        menores++;
                    }
                }
            }
            return menores <= 1;
        }

        private Movimento jogadaDoOllama(List<Movimento> legais) {
            // No dificil a gente filtra antes; no resto o modelo ve todas as jogadas legais.
            List<Movimento> candidatos = dificuldade == 3 ? melhoresJogadas(legais) : legais;
            Map<String, Movimento> porUci = new LinkedHashMap<>();
            for (Movimento movimento : candidatos) {
                porUci.put(movimento.uci(), movimento);
            }
            double temperatura = switch (dificuldade) {
                case 1 -> 1.0;
                case 2 -> 0.3;
                default -> 0.0;
            };

            String prompt = montarPrompt(candidatos);
            for (int tentativa = 1; tentativa <= 2; tentativa++) {
                try {
                    String resposta = ollama.gerar(prompt, temperatura);
                    String uci = extrairUci(resposta, porUci.keySet());
                    if (uci != null) {
                        return porUci.get(uci);
                    }
                    prompt = montarPrompt(candidatos) + "\nYour previous answer was: " + resposta
                            + "\nThat is not a legal move. Reply with exactly one move from the list.";
                } catch (Exception ex) {
                    System.out.println(Cores.erro("Falha ao consultar o Ollama: " + ex.getMessage()));
                    break;
                }
            }

            if (dificuldade == 3) {
                System.out.println(Cores.aviso("Ollama nao respondeu direito; jogando a melhor da busca local."));
                return candidatos.get(0);
            }
            System.out.println(Cores.aviso("Ollama nao respondeu uma jogada valida; jogando aleatorio."));
            return legais.get(random.nextInt(legais.size()));
        }

        private List<Movimento> melhoresJogadas(List<Movimento> legais) {
            // Dificil: uma busca rasga pra ranquear e mandar so as 4 melhores pro Ollama.
            List<Movimento> ordenadas = new ArrayList<>(legais);
            Map<Movimento, Integer> notas = new LinkedHashMap<>();
            for (Movimento movimento : legais) {
                Posicao depois = posicao.aplicar(movimento);
                notas.put(movimento,
                        minimax(depois, 1, ollamaBrancas, Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            ordenadas.sort((a, b) -> Integer.compare(notas.get(b), notas.get(a)));
            return new ArrayList<>(ordenadas.subList(0, Math.min(4, ordenadas.size())));
        }

        private String montarPrompt(List<Movimento> legais) {
            // Prompt em ingles porque modelo pequeno entende melhor assim.
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are playing chess as ")
                    .append(posicao.brancasJogam ? "White" : "Black").append(".\n");
            prompt.append("Level: ")
                    .append(dificuldade == 1 ? "easy" : dificuldade == 2 ? "medium" : "hard")
                    .append(".\n");
            prompt.append(switch (dificuldade) {
                case 1 -> "Play like a beginner: choose any legal move, don't think too hard.\n";
                case 2 -> "Play a reasonable, solid move.\n";
                default -> "You are strong: these are the best candidates from a quick search. Pick the strongest.\n";
            });
            prompt.append("\nFEN: ").append(posicao.paraFen()).append("\n\n");
            prompt.append("Board (uppercase = White):\n").append(diagramaParaIa()).append("\n\n");
            prompt.append("Legal moves in UCI format:\n");
            for (int i = 0; i < legais.size(); i++) {
                if (i > 0) {
                    prompt.append(' ');
                }
                prompt.append(legais.get(i).uci());
            }
            prompt.append("\n\nReply with only one move from the list, for example e2e4. No explanation.");
            return prompt.toString();
        }

        private String diagramaParaIa() {
            StringBuilder texto = new StringBuilder();
            for (int linha = 0; linha < 8; linha++) {
                texto.append(8 - linha).append(' ');
                for (int coluna = 0; coluna < 8; coluna++) {
                    char peca = posicao.tabuleiro[linha][coluna];
                    texto.append(peca == '.' ? '.' : Posicao.fenChar(peca));
                    if (coluna < 7) {
                        texto.append(' ');
                    }
                }
                texto.append('\n');
            }
            texto.append("  a b c d e f g h");
            return texto.toString();
        }

        private static String extrairUci(String resposta, Set<String> legais) {
            // O modelo as vezes enrola; limpa tudo e procura um UCI valido no meio do texto.
            String limpo = resposta.toLowerCase().replaceAll("[^a-z0-9]", "");
            for (int tamanho = 5; tamanho >= 4; tamanho--) {
                for (int i = 0; i + tamanho <= limpo.length(); i++) {
                    String candidato = limpo.substring(i, i + tamanho);
                    if (legais.contains(candidato)) {
                        return candidato;
                    }
                }
            }
            return null;
        }

        private String nomeDificuldade() {
            return switch (dificuldade) {
                case 1 -> "facil";
                case 2 -> "medio";
                default -> "dificil";
            };
        }

        private int lerOpcao(String mensagem, int minimo, int maximo) {
            while (true) {
                System.out.print(mensagem);
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    encerrar = true;
                    return minimo;
                }
                try {
                    int valor = Integer.parseInt(scanner.nextLine().trim());
                    if (valor >= minimo && valor <= maximo) {
                        return valor;
                    }
                } catch (NumberFormatException ignored) {
                }
                System.out.println(Cores.erro("Opcao invalida. Tente de novo."));
            }
        }

        private boolean confirmar(String mensagem) {
            System.out.print(mensagem);
            if (!scanner.hasNextLine()) {
                System.out.println();
                encerrar = true;
                return false;
            }
            return scanner.nextLine().trim().equalsIgnoreCase("s");
        }
    }

    private static final class Posicao {

        final char[][] tabuleiro = new char[8][8];
        boolean brancasJogam = true;
        boolean[] direitos = {true, true, true, true};
        int epLinha = -1;
        int epColuna = -1;
        int meioMovimentos;

        static Posicao inicial() {
            Posicao posicao = new Posicao();
            String[] linhas = {
                    "tcbdrbct",
                    "pppppppp",
                    "........",
                    "........",
                    "........",
                    "........",
                    "PPPPPPPP",
                    "TCBDRBCT"
            };
            for (int linha = 0; linha < 8; linha++) {
                for (int coluna = 0; coluna < 8; coluna++) {
                    posicao.tabuleiro[linha][coluna] = linhas[linha].charAt(coluna);
                }
            }
            return posicao;
        }

        String paraFen() {
            // FEN e o "endereco" da posicao; mando isso pro Ollama junto com o tabuleiro.
            StringBuilder fen = new StringBuilder();
            for (int linha = 0; linha < 8; linha++) {
                int vazias = 0;
                for (int coluna = 0; coluna < 8; coluna++) {
                    char peca = tabuleiro[linha][coluna];
                    if (peca == '.') {
                        vazias++;
                    } else {
                        if (vazias > 0) {
                            fen.append(vazias);
                            vazias = 0;
                        }
                        fen.append(fenChar(peca));
                    }
                }
                if (vazias > 0) {
                    fen.append(vazias);
                }
                if (linha < 7) {
                    fen.append('/');
                }
            }
            fen.append(brancasJogam ? " w " : " b ");
            StringBuilder direitosFen = new StringBuilder();
            if (direitos[0]) {
                direitosFen.append('K');
            }
            if (direitos[1]) {
                direitosFen.append('Q');
            }
            if (direitos[2]) {
                direitosFen.append('k');
            }
            if (direitos[3]) {
                direitosFen.append('q');
            }
            fen.append(direitosFen.length() == 0 ? "-" : direitosFen.toString());
            fen.append(' ');
            if (epLinha >= 0) {
                fen.append((char) ('a' + epColuna)).append(8 - epLinha);
            } else {
                fen.append('-');
            }
            fen.append(' ').append(meioMovimentos).append(" 1");
            return fen.toString();
        }

        static char fenChar(char peca) {
            // Traduz as letras em portugues pras letras padrao do xadrez (K/Q/R/B/N).
            char tipo = switch (Character.toLowerCase(peca)) {
                case 'r' -> 'k';
                case 'd' -> 'q';
                case 't' -> 'r';
                case 'c' -> 'n';
                default -> Character.toLowerCase(peca);
            };
            return Character.isUpperCase(peca) ? Character.toUpperCase(tipo) : tipo;
        }

        Posicao copiar() {
            Posicao copia = new Posicao();
            for (int linha = 0; linha < 8; linha++) {
                copia.tabuleiro[linha] = tabuleiro[linha].clone();
            }
            copia.brancasJogam = brancasJogam;
            copia.direitos = direitos.clone();
            copia.epLinha = epLinha;
            copia.epColuna = epColuna;
            copia.meioMovimentos = meioMovimentos;
            return copia;
        }

        Posicao aplicar(Movimento movimento) {
            // Copia a posicao e aplica a jogada, sem sujar a original.
            Posicao copia = copiar();
            char peca = copia.tabuleiro[movimento.l1][movimento.c1];
            char capturada = copia.tabuleiro[movimento.l2][movimento.c2];

            copia.tabuleiro[movimento.l1][movimento.c1] = '.';
            copia.tabuleiro[movimento.l2][movimento.c2] =
                    movimento.promocao != 0 ? movimento.promocao : peca;

            if (movimento.enPassant) {
                // En passant: come o peao vizinho que passou reto.
                copia.tabuleiro[movimento.l1][movimento.c2] = '.';
            }

            if (movimento.roque) {
                // Roque: o rei pula duas casas e a torre vem do outro lado.
                if (movimento.c2 == 6) {
                    copia.tabuleiro[movimento.l1][5] = copia.tabuleiro[movimento.l1][7];
                    copia.tabuleiro[movimento.l1][7] = '.';
                } else {
                    copia.tabuleiro[movimento.l1][3] = copia.tabuleiro[movimento.l1][0];
                    copia.tabuleiro[movimento.l1][0] = '.';
                }
            }

            // Se o rei ou uma torre saiu do lugar, o roque daquele lado morre.
            if (peca == 'R') {
                copia.direitos[0] = false;
                copia.direitos[1] = false;
            } else if (peca == 'r') {
                copia.direitos[2] = false;
                copia.direitos[3] = false;
            }
            if (movimento.l1 == 7 && movimento.c1 == 7) {
                copia.direitos[0] = false;
            } else if (movimento.l1 == 7 && movimento.c1 == 0) {
                copia.direitos[1] = false;
            } else if (movimento.l1 == 0 && movimento.c1 == 7) {
                copia.direitos[2] = false;
            } else if (movimento.l1 == 0 && movimento.c1 == 0) {
                copia.direitos[3] = false;
            }
            // E se comeram a torre no canto, o direito tambem cai.
            if (movimento.l2 == 7 && movimento.c2 == 7) {
                copia.direitos[0] = false;
            } else if (movimento.l2 == 7 && movimento.c2 == 0) {
                copia.direitos[1] = false;
            } else if (movimento.l2 == 0 && movimento.c2 == 7) {
                copia.direitos[2] = false;
            } else if (movimento.l2 == 0 && movimento.c2 == 0) {
                copia.direitos[3] = false;
            }

            // So existe en passant logo depois de um peao andar duas casas.
            copia.epLinha = -1;
            copia.epColuna = -1;
            if (Character.toUpperCase(peca) == 'P' && Math.abs(movimento.l2 - movimento.l1) == 2) {
                copia.epLinha = (movimento.l1 + movimento.l2) / 2;
                copia.epColuna = movimento.c1;
            }

            // Regra dos 50 movimentos: zera em movimento de peao ou captura.
            if (Character.toUpperCase(peca) == 'P' || capturada != '.' || movimento.enPassant) {
                copia.meioMovimentos = 0;
            } else {
                copia.meioMovimentos++;
            }

            copia.brancasJogam = !copia.brancasJogam;
            return copia;
        }

        List<Movimento> gerarPseudo(int linha, int coluna) {
            List<Movimento> movimentos = new ArrayList<>();
            char peca = tabuleiro[linha][coluna];
            if (peca == '.') {
                return movimentos;
            }
            boolean brancas = ehBranca(peca);
            char tipo = Character.toUpperCase(peca);

            if (tipo == 'P') {
                gerarPeao(linha, coluna, brancas, movimentos);
            } else if (tipo == 'C') {
                gerarCavalo(linha, coluna, brancas, movimentos);
            } else if (tipo == 'B') {
                gerarDeslizante(linha, coluna, brancas, movimentos, DIAGONAIS);
            } else if (tipo == 'T') {
                gerarDeslizante(linha, coluna, brancas, movimentos, ORTOGONAIS);
            } else if (tipo == 'D') {
                gerarDeslizante(linha, coluna, brancas, movimentos, DIAGONAIS);
                gerarDeslizante(linha, coluna, brancas, movimentos, ORTOGONAIS);
            } else if (tipo == 'R') {
                gerarRei(linha, coluna, brancas, movimentos);
            }
            return movimentos;
        }

        private void gerarPeao(int linha, int coluna, boolean brancas, List<Movimento> movimentos) {
            // Peao anda reto, come na diagonal, pode andar 2 na primeira e tem en passant.
            int direcao = brancas ? -1 : 1;
            int linhaInicial = brancas ? 6 : 1;
            int linhaFinal = brancas ? 0 : 7;
            int frente = linha + direcao;

            if (dentro(frente, coluna) && tabuleiro[frente][coluna] == '.') {
                adicionarPeao(linha, coluna, frente, coluna, linhaFinal, brancas, movimentos);
                int duasFrentes = linha + 2 * direcao;
                if (linha == linhaInicial && tabuleiro[duasFrentes][coluna] == '.') {
                    movimentos.add(new Movimento(linha, coluna, duasFrentes, coluna));
                }
            }

            for (int dc : new int[]{-1, 1}) {
                int nl = linha + direcao;
                int nc = coluna + dc;
                if (!dentro(nl, nc)) {
                    continue;
                }
                char alvo = tabuleiro[nl][nc];
                if (alvo != '.' && !daCor(alvo, brancas)) {
                    adicionarPeao(linha, coluna, nl, nc, linhaFinal, brancas, movimentos);
                } else if (alvo == '.' && nl == epLinha && nc == epColuna) {
                    char peaoAdversario = brancas ? 'p' : 'P';
                    if (tabuleiro[linha][nc] == peaoAdversario) {
                        movimentos.add(new Movimento(linha, coluna, nl, nc, (char) 0, false, true));
                    }
                }
            }
        }

        private void adicionarPeao(int l1, int c1, int l2, int c2, int linhaFinal,
                                   boolean brancas, List<Movimento> movimentos) {
            if (l2 == linhaFinal) {
                // Chegou na ultima linha: vira Dama, Torre, Bispo ou Cavalo.
                char[] opcoes = brancas
                        ? new char[]{'D', 'T', 'B', 'C'}
                        : new char[]{'d', 't', 'b', 'c'};
                for (char opcao : opcoes) {
                    movimentos.add(new Movimento(l1, c1, l2, c2, opcao, false, false));
                }
            } else {
                movimentos.add(new Movimento(l1, c1, l2, c2));
            }
        }

        private void gerarCavalo(int linha, int coluna, boolean brancas, List<Movimento> movimentos) {
            for (int[] salto : SALTOS_CAVALO) {
                int nl = linha + salto[0];
                int nc = coluna + salto[1];
                if (!dentro(nl, nc)) {
                    continue;
                }
                char alvo = tabuleiro[nl][nc];
                if (alvo == '.' || !daCor(alvo, brancas)) {
                    movimentos.add(new Movimento(linha, coluna, nl, nc));
                }
            }
        }

        private void gerarDeslizante(int linha, int coluna, boolean brancas,
                                     List<Movimento> movimentos, int[][] direcoes) {
            for (int[] direcao : direcoes) {
                int nl = linha + direcao[0];
                int nc = coluna + direcao[1];
                while (dentro(nl, nc)) {
                    char alvo = tabuleiro[nl][nc];
                    if (alvo == '.') {
                        movimentos.add(new Movimento(linha, coluna, nl, nc));
                    } else {
                        if (!daCor(alvo, brancas)) {
                            movimentos.add(new Movimento(linha, coluna, nl, nc));
                        }
                        break;
                    }
                    nl += direcao[0];
                    nc += direcao[1];
                }
            }
        }

        private void gerarRei(int linha, int coluna, boolean brancas, List<Movimento> movimentos) {
            // Rei anda 1 casa; o roque e tratado aqui tambem.
            for (int dl = -1; dl <= 1; dl++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dl == 0 && dc == 0) {
                        continue;
                    }
                    int nl = linha + dl;
                    int nc = coluna + dc;
                    if (!dentro(nl, nc)) {
                        continue;
                    }
                    char alvo = tabuleiro[nl][nc];
                    if (alvo == '.' || !daCor(alvo, brancas)) {
                        movimentos.add(new Movimento(linha, coluna, nl, nc));
                    }
                }
            }

            if (emXeque(brancas)) {
                return;
            }
            int linhaBase = brancas ? 7 : 0;
            int base = brancas ? 0 : 2;
            if (linha != linhaBase || coluna != 4) {
                return;
            }
            char torre = brancas ? 'T' : 't';
            if (direitos[base]
                    && tabuleiro[linhaBase][7] == torre
                    && tabuleiro[linhaBase][5] == '.'
                    && tabuleiro[linhaBase][6] == '.'
                    && !casaAtacada(linhaBase, 5, !brancas)
                    && !casaAtacada(linhaBase, 6, !brancas)) {
                movimentos.add(new Movimento(linha, coluna, linhaBase, 6, (char) 0, true, false));
            }
            if (direitos[base + 1]
                    && tabuleiro[linhaBase][0] == torre
                    && tabuleiro[linhaBase][1] == '.'
                    && tabuleiro[linhaBase][2] == '.'
                    && tabuleiro[linhaBase][3] == '.'
                    && !casaAtacada(linhaBase, 3, !brancas)
                    && !casaAtacada(linhaBase, 2, !brancas)) {
                movimentos.add(new Movimento(linha, coluna, linhaBase, 2, (char) 0, true, false));
            }
        }

        boolean casaAtacada(int linha, int coluna, boolean porBrancas) {
            // Olha se alguma peca inimiga consegue bater nessa casa.
            char peao = porBrancas ? 'P' : 'p';
            int linhaPeao = linha + (porBrancas ? 1 : -1);
            if (dentro(linhaPeao, coluna - 1) && tabuleiro[linhaPeao][coluna - 1] == peao) {
                return true;
            }
            if (dentro(linhaPeao, coluna + 1) && tabuleiro[linhaPeao][coluna + 1] == peao) {
                return true;
            }

            char cavalo = porBrancas ? 'C' : 'c';
            for (int[] salto : SALTOS_CAVALO) {
                int nl = linha + salto[0];
                int nc = coluna + salto[1];
                if (dentro(nl, nc) && tabuleiro[nl][nc] == cavalo) {
                    return true;
                }
            }

            char rei = porBrancas ? 'R' : 'r';
            for (int dl = -1; dl <= 1; dl++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dl == 0 && dc == 0) {
                        continue;
                    }
                    int nl = linha + dl;
                    int nc = coluna + dc;
                    if (dentro(nl, nc) && tabuleiro[nl][nc] == rei) {
                        return true;
                    }
                }
            }

            char torre = porBrancas ? 'T' : 't';
            char dama = porBrancas ? 'D' : 'd';
            for (int[] direcao : ORTOGONAIS) {
                int nl = linha + direcao[0];
                int nc = coluna + direcao[1];
                while (dentro(nl, nc)) {
                    char peca = tabuleiro[nl][nc];
                    if (peca != '.') {
                        if (peca == torre || peca == dama) {
                            return true;
                        }
                        break;
                    }
                    nl += direcao[0];
                    nc += direcao[1];
                }
            }

            char bispo = porBrancas ? 'B' : 'b';
            for (int[] direcao : DIAGONAIS) {
                int nl = linha + direcao[0];
                int nc = coluna + direcao[1];
                while (dentro(nl, nc)) {
                    char peca = tabuleiro[nl][nc];
                    if (peca != '.') {
                        if (peca == bispo || peca == dama) {
                            return true;
                        }
                        break;
                    }
                    nl += direcao[0];
                    nc += direcao[1];
                }
            }
            return false;
        }

        boolean emXeque(boolean brancas) {
            char rei = brancas ? 'R' : 'r';
            for (int linha = 0; linha < 8; linha++) {
                for (int coluna = 0; coluna < 8; coluna++) {
                    if (tabuleiro[linha][coluna] == rei) {
                        return casaAtacada(linha, coluna, !brancas);
                    }
                }
            }
            return false;
        }

        static boolean dentro(int linha, int coluna) {
            return linha >= 0 && linha < 8 && coluna >= 0 && coluna < 8;
        }

        static boolean ehBranca(char peca) {
            return peca != '.' && Character.isUpperCase(peca);
        }

        static boolean daCor(char peca, boolean brancas) {
            return brancas ? ehBranca(peca) : peca != '.' && Character.isLowerCase(peca);
        }
    }

    // Cliente HTTP na unha, sem biblioteca externa. Fala com o Ollama em localhost.
    private static final class Ollama {

        private final HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private final String url = System.getenv().getOrDefault("OLLAMA_URL", "http://127.0.0.1:11434");
        private final String modelo = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:3b");

        String url() {
            return url;
        }

        String modelo() {
            return modelo;
        }

        boolean disponivel() {
            try {
                HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url + "/api/tags"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> resposta = cliente.send(requisicao,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return resposta.statusCode() == 200;
            } catch (IOException ex) {
                return false;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        String gerar(String prompt, double temperatura) throws IOException, InterruptedException {
            // Chama /api/generate e pega so o campo "response" do JSON.
            String json = "{\"model\":\"" + escapar(modelo) + "\",\"prompt\":\"" + escapar(prompt)
                    + "\",\"stream\":false,\"options\":{\"temperature\":" + temperatura
                    + ",\"num_predict\":24}}";
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(180))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resposta = cliente.send(requisicao,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() != 200) {
                throw new IOException("HTTP " + resposta.statusCode());
            }
            String conteudo = campo(resposta.body(), "response");
            if (conteudo == null || conteudo.isBlank()) {
                throw new IOException("resposta vazia do modelo");
            }
            return conteudo;
        }

        private static String campo(String json, String nome) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(nome)
                    + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
            return matcher.find() ? desescapar(matcher.group(1)) : null;
        }

        // JSON nao aceita quebra de linha nem aspas soltas; a gente escapa na mao.
        private static String escapar(String texto) {
            StringBuilder json = new StringBuilder();
            for (char caractere : texto.toCharArray()) {
                switch (caractere) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    default -> {
                        if (caractere < 32) {
                            json.append(String.format("\\u%04x", (int) caractere));
                        } else {
                            json.append(caractere);
                        }
                    }
                }
            }
            return json.toString();
        }

        private static String desescapar(String texto) {
            StringBuilder saida = new StringBuilder();
            for (int i = 0; i < texto.length(); i++) {
                char caractere = texto.charAt(i);
                if (caractere != '\\' || i + 1 >= texto.length()) {
                    saida.append(caractere);
                    continue;
                }
                char proximo = texto.charAt(++i);
                switch (proximo) {
                    case 'n' -> saida.append('\n');
                    case 'r' -> saida.append('\r');
                    case 't' -> saida.append('\t');
                    case '"' -> saida.append('"');
                    case '\\' -> saida.append('\\');
                    case '/' -> saida.append('/');
                    case 'u' -> {
                        if (i + 4 < texto.length()) {
                            saida.append((char) Integer.parseInt(texto.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> saida.append(proximo);
                }
            }
            return saida.toString();
        }
    }

    private static final class Movimento {

        final int l1;
        final int c1;
        final int l2;
        final int c2;
        final char promocao;
        final boolean roque;
        final boolean enPassant;

        Movimento(int l1, int c1, int l2, int c2) {
            this(l1, c1, l2, c2, (char) 0, false, false);
        }

        Movimento(int l1, int c1, int l2, int c2, char promocao,
                  boolean roque, boolean enPassant) {
            this.l1 = l1;
            this.c1 = c1;
            this.l2 = l2;
            this.c2 = c2;
            this.promocao = promocao;
            this.roque = roque;
            this.enPassant = enPassant;
        }

        String notacao() {
            StringBuilder texto = new StringBuilder();
            texto.append(casa(l1, c1)).append(casa(l2, c2));
            if (promocao != 0) {
                texto.append('=').append(Character.toUpperCase(promocao));
            }
            return texto.toString();
        }

        // UCI e o formato que o Ollama entende: e2e4, e7e8q...
        String uci() {
            StringBuilder texto = new StringBuilder();
            texto.append(casa(l1, c1)).append(casa(l2, c2));
            if (promocao != 0) {
                texto.append(switch (Character.toUpperCase(promocao)) {
                    case 'D' -> 'q';
                    case 'T' -> 'r';
                    case 'B' -> 'b';
                    case 'C' -> 'n';
                    default -> 'q';
                });
            }
            return texto.toString();
        }

        private static String casa(int linha, int coluna) {
            return String.valueOf((char) ('a' + coluna)) + (8 - linha);
        }
    }
}
