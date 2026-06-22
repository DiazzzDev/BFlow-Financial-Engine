# BFlow Backend

![GitHub stars](https://img.shields.io/github/stars/DiazzzDev/BFlow-Backend-Open-Source?style=social)
![GitHub forks](https://img.shields.io/github/forks/DiazzzDev/BFlow-Backend-Open-Source?style=social)
![GitHub license](https://img.shields.io/github/license/DiazzzDev/BFlow-Backend-Open-Source)

BFlow is a multi-wallet financial control system designed to help individuals and small groups understand, organize and master how their money actually moves.

## Why BFlow Exists

BFlow was born from a real and daily problem: financial disorder.

Managing money across multiple wallets, cards, cash, and shared funds becomes chaotic very quickly. 
Small daily expenses silently destroy weekly budgets, and by mid-week it’s unclear how much money is actually available.

Many people — especially students — try to solve this with:
- Custom Excel sheets
- Notes
- Or nothing at all

Existing apps often require bank credentials, feel intrusive, or are not designed for flexible multi-wallet and shared budgeting scenarios.

BFlow aims to provide clarity without complexity.

## The Problem It Solves

- Lack of visibility across multiple wallets and payment sources.
- No clear understanding of remaining budget mid-week.
- No structured way to manage shared savings (e.g., group trips).
- Difficulty identifying spending patterns.
- Distrust in financial apps that request banking credentials unnecessarily.

BFlow focuses on intentional financial tracking without requiring bank integrations.

## Vision

BFlow is not just a CRUD expense tracker.

The vision is to evolve into:

- A multi-wallet budgeting system
- Shared wallets with role-based access
- Monthly financial summaries
- Advanced filtering and spending analytics
- Recurring income automation (e.g., salary simulation via cron jobs)
- Clean, enterprise-grade backend architecture
- Potential SaaS evolution once domain integrity is fully validated

This project is both:
- A personal financial tool
- A portfolio-grade system built with scalability and security in mind

## Current Status

MVP in active development.

Implemented:
- User authentication (AWS Cognito)
- Multi-wallet support
- Expense registration
- Basic transactional consistency
- Budget rules engine

Planned:
- Redis-based caching
- Idempotency layer for future payment integration
- Shared wallet roles

## Architecture & Engineering Philosophy

Current Stack

- Spring Boot
- PostgreSQL
- AWS for cognito Auth
- Docker

### Planned Additions

- Redis (caching, idempotency, rate limiting)

The focus is on:
- Clear business rules
- Secure multi-user wallet access
- Transactional consistency
- Scalability-oriented design
- Real-world financial logic

## Non-Goals (MVP Phase)

- No direct bank integrations
- No AI-based financial predictions (yet)
- No premature microservices architecture

The MVP focuses strictly on correctness, security, and domain integrity.

## Getting started

### Requirements
- Docker Desktop (you can install it from [Docker](https://www.docker.com/products/docker-desktop/))
- VSCode
- Dev Containers extension [here](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

### Setup

- Open Docker Desktop during the process, this is important.
- Open VSCode and open the project folder
- A notification will appear on the corner
- Click on the "Reopen in Container" option, then wait until everything is setup. (This may take 8–10 minutes on first run)
- In the process there will be a pop-up asking for your WakaTime Api Key (This is a tool that I personally use, if you dont use it just click 'Enter' and nothing will happend). If you want to use WakaTime too you can add it to your dashboards [here](https://wakatime.com/settings/api-key)
- Watch the containers in Docker Desktop, everyone has to run. (The status is now green on docker)
- Once everything is downloaded you can run the application with the following command:

```bash
    ./mvnw spring-boot:run
```

### JWT RSA Keys (RS256)

This project uses RSA (RS256) for signing JWT access tokens.

#### Generate keys (development only)

```bash
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in private.pem -out public.pem
```

### Run testing:

If you want to test if everything is good for a pull request you can test it with the following command:

```bash
    ./mvnw test
```

## Troubleshooting during setup

### My containers had trouble:

Try this first:
- Close all VSCode windows and Docker Desktop, 
- Reopen Docker Desktop first and then VSCode
- Open the folder project
- Click on the notification
- Wait again and see if the containers are running on Docker Desktop

If that didnt work usually is because Docker sometimes requires you to restart your machine and try the steps I wrote before.

### I changed my .env variables and they doesn't work
- You can run "echo $ENV_VAR" (Example: "echo $DB_URL") to display how the container reads the variable.
- If there is an older value press F1 or CTRL + SHIFT + P and run "Dev Containers: Rebuild Container"
- Run "echo $ENV_VAR" again before run the app and now you can see if it reads the value you changed
