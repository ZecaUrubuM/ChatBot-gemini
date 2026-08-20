# ChatBot Gemini

Monorepo do chatbot de supermercado: API Java/Spring Boot com Google Gemini (via endpoint OpenAI-compatible) e interface web estática.

## Estrutura

```
.
├── backend/                 # Java / Spring Boot (Maven)
│   ├── pom.xml
│   └── src/
├── frontend/                # Interface do chat
│   └── index.html
├── .gitignore
└── README.md
```

O repositório Git fica **somente na raiz**. Não há pasta `.git` dentro de `backend` ou `frontend`.

## Backend

Requisitos: Java 21 e Maven.

```bash
cd backend
mvn spring-boot:run
```

Defina `GEMINI_API_KEY` no ambiente (não commite a chave). A API sobe em `http://localhost:8080`.

O back-end usa `langchain4j-open-ai-spring-boot-starter` apontando para `https://generativelanguage.googleapis.com/v1beta/openai/` (modelo `gemini-3.6-flash`). O `OpenAiChatModel` é um `@Bean` manual: o payload **não** envia `temperature` nem `thinking_budget`. No loop de `@Tool`, um interceptor HTTP reinsere `tool_calls[].extra_content.google.thought_signature` (o conector OpenAI do LangChain4j descarta esse campo).

- Chat: `POST /api/chat`
- Console H2: `http://localhost:8080/h2-console`

Configuração: `backend/src/main/resources/application.properties`.

## Frontend

Não abra o HTML como `file://` (origem `"null"`, bloqueada pelo CORS). Sirva a pasta na porta 5173:

```bash
cd frontend
npx --yes serve -p 5173
```

Depois acesse `http://localhost:5173` (ou `http://127.0.0.1:5173`). O chat chama `http://localhost:8080/api/chat`.

## Checklist de teste

1. Defina `GEMINI_API_KEY` e suba o backend: `cd backend` e `mvn spring-boot:run`.
2. Confirme a API em `http://localhost:8080` (sem erro de porta ocupada).
3. Em outro terminal, sirva o front: `cd frontend` e `npx --yes serve -p 5173`.
4. Abra **somente** `http://localhost:5173` no navegador (não o arquivo local).
5. Envie uma mensagem no chat (ex.: "Quais produtos vocês têm?").
6. A resposta do bot deve aparecer; se falhar, abra o DevTools (F12) → Network e confira se `POST /api/chat` retorna 200 e se o preflight `OPTIONS` tem `Access-Control-Allow-Origin`.
