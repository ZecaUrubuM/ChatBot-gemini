# ChatBot Gemini

Monorepo do chatbot de supermercado: API Java/Spring Boot com Google Gemini e interface web estática.

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

- Chat: `POST /api/chat`
- Console H2: `http://localhost:8080/h2-console`

Configuração: `backend/src/main/resources/application.properties`.

## Frontend

Abra `frontend/index.html` no navegador ou sirva a pasta, por exemplo:

```bash
cd frontend
npx --yes serve -p 5173
```

O chat chama `http://localhost:8080/api/chat`. Mantenha o backend rodando.
