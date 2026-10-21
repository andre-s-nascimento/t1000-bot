## 🤖 Auto-Responses

O bot possui um sistema de respostas automáticas configurável via arquivo JSON (`auto-responses.json`). As regras são acionadas por palavras-chave (triggers) e podem ter restrições de horário, respostas específicas por usuário e **múltiplas animações**.

### 📄 Formato do JSON

A partir da versão **2.6.1**, uma regra pode ser configurada de três formas (a precedência é de cima para baixo):

#### 1. Múltiplos Pares (Texto + Animação) — _Recomendado_

Permite que cada animação tenha seu próprio texto. No momento do disparo, o bot sorteia **um par inteiro** (texto e animação juntos).

```json
{
  "rules": {
    "saudacao": {
      "triggers": ["bom dia"],
      "responses": [
        {
          "text": "Bom dia! Que seu dia seja épico!",
          "animation": "https://exemplo.com/bom-dia-1.gif"
        },
        {
          "text": "Acorda, campeão! O T-1000 já está de pé.",
          "animation": "https://exemplo.com/bom-dia-2.gif"
        }
      ],
      "timeRange": { "start": "05:00", "end": "11:59" }
    }
  }
}
```

#### 2. Lista de Animações (Texto Único)

Mantém o texto fixo (`response`) e sorteia apenas a animação.

```json
{
  "rules": {
    "saudacao": {
      "triggers": ["bom dia"],
      "response": "Bom dia!",
      "animations": [
        "https://exemplo.com/bom-dia-1.gif",
        "https://exemplo.com/bom-dia-2.gif"
      ]
    }
  }
}
```

#### 3. Animação Única (Formato Legado)

Formato original, com texto e animação fixos.

```json
{
  "rules": {
    "saudacao": {
      "triggers": ["bom dia"],
      "response": "Bom dia!",
      "animation": "https://exemplo.com/bom-dia.gif"
    }
  }
}
```

### ⚙️ Regras de Precedência

Se uma regra tiver mais de um campo de animação, o bot aplica a seguinte ordem:

1. `responses` (se existir e não estiver vazio)
2. `animations` (se existir e não estiver vazio)
3. `animation` (se existir e não estiver vazio)
4. Nenhum deles (a regra envia apenas texto)

### 🎲 Sorteio

O sorteio ocorre **a cada disparo** (não no carregamento do JSON). Ele usa `ThreadLocalRandom`, garantindo que não há contenção entre threads.

### ⏱️ Restrição "Once per Day"

Alguns triggers (configurados em `auto.response.once-per-day-triggers`, como `bom dia`) só respondem **uma vez por dia por usuário**. Isso evita spam.
_Nota para testes:_ se você estiver testando pelo painel admin, deixe o campo `User ID` em branco para ignorar essa regra e poder testar o sorteio quantas vezes quiser.
