# Chess in Java

Terminal chess game for two players or against a local Ollama model. The whole game runs from a single Java file with no external libraries.

## Run

Requires Java 21+.

```powershell
.\jogar.cmd
```

Or run it directly:

```powershell
java Xadrez.java
```

## Ollama (optional)

```powershell
ollama serve
```

The default model is `qwen2.5:3b`. Change it with `$env:OLLAMA_MODEL`.

## How to play

Enter moves like `e2 e4`. The menu shows the available modes. Type `ajuda` for commands or `sair` to exit.

The game validates legal moves and supports check, checkmate, stalemate, castling, en passant, promotion and draws.
