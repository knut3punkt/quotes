package no.esotericgames.quotes.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabase() {
    val config = environment.config.config("storage")
    if (config.propertyOrNull("user") == null || config.propertyOrNull("password") == null) {
        log.warn("DB_USER/DB_PASSWORD not set — skipping database setup")
        return
    }
    val dataSource = createDataSource(config)
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
    Database.connect(dataSource)
}

private fun createDataSource(config: ApplicationConfig): HikariDataSource {
    val host = config.property("host").getString()
    val port = config.property("port").getString()
    val database = config.property("database").getString()
    val hikariConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        username = config.property("user").getString()
        password = config.property("password").getString()
    }
    return HikariDataSource(hikariConfig)
}
