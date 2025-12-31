# Anleitung: DB nicht lokal, sondern auf einem Server

Diese Anleitung beschreibt zwei Wege, damit die Datenbank nicht mehr lokal in `./data` liegt, sondern auf einem Server.

## Variante A: H2 im Server-Modus (schnell, aber nur bedingt fuer Produktion)

### 1) H2 auf dem Server starten
1. H2 auf den Server kopieren (JAR).
2. Server starten (Beispiel):
   ```bash
   java -cp h2*.jar org.h2.tools.Server -tcp -tcpAllowOthers -ifExists -tcpPort 9092
   ```
3. Datenbankdatei liegt auf dem Server (z. B. `~/wgdb`).

### 2) Anwendungskonfiguration anpassen
In `src/main/resources/application.properties` die URL aendern:
```properties
spring.datasource.url=jdbc:h2:tcp://SERVER:9092/~/wgdb;CIPHER=AES
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

### 3) Optional
- H2-Console deaktivieren, wenn nicht noetig:
  ```properties
  spring.h2.console.enabled=false
  ```

## Variante B: PostgreSQL (empfohlen fuer Produktion)

### 1) PostgreSQL auf dem Server
1. PostgreSQL installieren.
2. Datenbank und Benutzer anlegen:
   ```sql
   CREATE DATABASE wgdb;
   CREATE USER wguser WITH PASSWORD 'secret';
   GRANT ALL PRIVILEGES ON DATABASE wgdb TO wguser;
   ```

### 2) Maven-Dependency anpassen
In `pom.xml` H2 entfernen und PostgreSQL hinzufuegen:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 3) application.properties anpassen
```properties
spring.datasource.url=jdbc:postgresql://SERVER:5432/wgdb
spring.datasource.username=wguser
spring.datasource.password=secret
spring.datasource.driverClassName=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### 4) Zugangsdaten per Env-Variablen (empfohlen)
Statt Klartext in `application.properties`:
```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```
Dann beim Start setzen:
```bash
DB_URL=jdbc:postgresql://SERVER:5432/wgdb \
DB_USER=wguser \
DB_PASSWORD=secret \
./mvnw spring-boot:run
```

## Hinweise
- Die lokale Datei `./data/wgdb` wird danach nicht mehr genutzt.
- Bei H2 im Server-Modus muss der TCP-Port (z. B. 9092) vom Client erreichbar sein.
- Fuer Produktion ist PostgreSQL/MySQL ueblich; H2 ist eher fuer Entwicklung/Tests gedacht.
