# BFlow Backend

![GitHub stars](https://img.shields.io/github/stars/DiazzzDev/BFlow-Backend-Open-Source?style=social)
![GitHub forks](https://img.shields.io/github/forks/DiazzzDev/BFlow-Backend-Open-Source?style=social)
![GitHub license](https://img.shields.io/github/license/DiazzzDev/BFlow-Backend-Open-Source)
[![CI](https://github.com/DiazzzDev/BFlow-Backend-Open-Source/actions/workflows/github-pipeline.yml/badge.svg)]

BFlow is a multi-wallet financial control system designed to help individuals and small groups understand, organize and master how their money actually moves.

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
- Long-term goal: validate the domain and evolve the platform through community feedback.

This project is both:
- Open-source platform focused on financial domain modeling and budgeting systems.
- A portfolio-grade system built with scalability and security in mind

## Current Status

MVP in active development.

Implemented:
- User authentication (AWS Cognito)
- Multi-wallet support
- Expense registration
- Basic transactional consistency
- Budget rules engine
- Rate limiter
- Idempotency layer

Planned:
- Redis-based caching
- Payment integration via Wompi
- Shared wallet roles

## Architecture & Engineering Philosophy

Current Stack

- Spring Boot
- PostgreSQL
- AWS Services (SES & Cognito)
- Docker

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

## API Documentation

Once the application is running you can have Swagger UI available at:

http://localhost:8080/swagger-ui/index.html

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