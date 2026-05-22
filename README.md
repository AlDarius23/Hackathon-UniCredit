# Hackathon-UniCredit

BeMyHelp is an AI-powered FinTech chatbot application developed for UniCredit. It streamlines customer onboarding through automated financial profiling, intelligent credit/portfolio scoring, and real-time interactive AI assistance.

## Features

- **AI-Driven Financial Chatbot:** Interactive conversational interface providing automated, intelligent answers to financial queries.
- **Automated User Profiling:** Core services (`ProfileScoringService`) that process and score user financial profiles dynamically.
- **Curated Financial Knowledge Base:** Automated web scraping components using JSoup to pull and store relevant financial FAQs.
- **Robust Architecture:** Clean separation of concerns with dedicated Controllers, Services, and Repositories for scalable data handling.

## Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Data JPA
- **Database:** PostgreSQL
- **Web Scraping & Parsing:** JSoup, Jackson
- **Frontend:** HTML5, JavaScript (Tailwind/Custom CSS)

## Setup & Installation

1. **Database Configuration:**
   - Ensure you have a local PostgreSQL instance running.
   - Create a database named `forum_db`.
   - Update the credentials in `src/main/resources/application.properties` if necessary:
     ```properties
     spring.datasource.url=jdbc:postgresql://localhost:5432/forum_db
     spring.datasource.username=postgres
     spring.datasource.password=YourPassword
     ```

2. **Run the Application:**
   - Navigate to the `back` directory.
   - Execute the following command to start the Spring Boot server:
     ```bash
     ./mvnw spring-boot:run
     ```
   - The backend will start on port `8081`.
