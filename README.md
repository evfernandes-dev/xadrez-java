# Chess in Java

Terminal chess game for two players or against a local Ollama model. Everything runs from a single Java file with no external libraries.

> The menu and code comments are in Portuguese; this README is in English.

## Install Java (once)

```powershell
winget install -e --id EclipseAdoptium.Temurin.21.JDK
```

Or download it from [adoptium.net](https://adoptium.net). Reopen the terminal after installing.

## Play

```powershell
.\jogar.cmd
```

Or run it directly:

```powershell
java Xadrez.java
```

## Menu

```text
1 - Player vs player
2 - Play against Ollama
0 - Exit
```

In Ollama mode you also choose the difficulty (Easy, Medium or Hard) and your side.

## How to play

- Enter origin and destination squares: `e2 e4`, `e2e4` or `e2-e4`.
- Castling: move the king two squares (`e1 g1` or `e1 c1`).
- En passant: move the pawn to the capture square.
- Promotion: choose `D` (queen), `T` (rook), `B` (bishop) or `C` (knight).
- `ajuda` shows the commands; `sair` exits.

Pieces: UPPERCASE for White, lowercase for Black. `P` pawn, `T` rook, `C` knight, `B` bishop, `D` queen, `R` king.

## Difficulty (Ollama)

- **Easy:** loose prompt and higher temperature.
- **Medium:** balanced play.
- **Hard:** a shallow local search shortlists the 4 best moves and Ollama picks one.

## Ollama (optional)

```powershell
ollama serve
```

- Default model: `qwen2.5:3b`.
- Change it with `$env:OLLAMA_MODEL = "llama3.2:1b"`.
- If Ollama is not running, player vs player still works.

## Rules

Full legal move validation, check, checkmate, stalemate, castling, en passant and promotion. Draw by the 50-move rule and insufficient material. Moves that leave your own king in check are rejected.

## Structure

```text
├── Xadrez.java   The whole game
├── jogar.cmd     Windows launcher
└── README.md
```
