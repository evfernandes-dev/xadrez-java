# Chess in Java

A terminal chess game for two players or against a local Ollama model. Everything runs from a single Java file with no external libraries.

> The game menu and the code comments are in Portuguese (this is a Brazilian project). This README is in English.

## Install Java (once)

```powershell
winget install -e --id EclipseAdoptium.Temurin.21.JDK
```

Close and reopen the terminal after installing. You can also download it from [adoptium.net](https://adoptium.net).

## Play

From the project folder:

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

In Ollama mode you also choose the difficulty (Easy, Medium or Hard) and which side you want to play.

## How to play

- Enter the origin and destination squares: `e2 e4`, `e2e4` or `e2-e4`.
- Castling: move the king two squares (`e1 g1` or `e1 c1`).
- En passant: move the pawn to the capture square.
- Promotion: choose `D` (queen), `T` (rook), `B` (bishop) or `C` (knight).
- `ajuda` shows the commands; `sair` exits.

## Pieces

- White pieces are UPPERCASE; black pieces are lowercase.
- `P` pawn, `T` rook, `C` knight, `B` bishop, `D` queen, `R` king.

## Difficulty (Ollama)

- **Easy:** loose prompt and higher temperature.
- **Medium:** balanced play.
- **Hard:** a shallow local search shortlists the 4 best moves and Ollama picks one.

## Ollama (optional)

```powershell
ollama serve
```

- Default model: `qwen2.5:3b`.
- Use another model: `$env:OLLAMA_MODEL = "llama3.2:1b"`.
- If Ollama is not running, player vs player still works.

## Rules

- Full legal move validation, check, checkmate and stalemate.
- Castling, en passant and promotion.
- Draw by the 50-move rule and insufficient material.
- Moves that leave your own king in check are rejected.
- Legal move generation, check, checkmate and stalemate are handled in the game logic.

## Project structure

```text
xadrez/
├── Xadrez.java   # the whole game
├── jogar.cmd     # Windows launcher
└── README.md
```
