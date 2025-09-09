package tech.nuqta.mooda.domain.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.UserEntity
import tech.nuqta.mooda.infrastructure.persistence.entity.UserIdentityEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.UserIdentityRepository
import tech.nuqta.mooda.infrastructure.persistence.repository.UserRepository
import tech.nuqta.mooda.infrastructure.security.GoogleIdTokenVerifier
import tech.nuqta.mooda.infrastructure.security.JwtSupport
import java.util.*

@Service
class AuthService(
    private val googleVerifier: GoogleIdTokenVerifier,
    private val userRepo: UserRepository,
    private val identityRepo: UserIdentityRepository,
    private val jwtSupport: JwtSupport
) {
    data class GoogleAuthResult(val accessToken: String, val expiresIn: Long)

    fun googleAuth(idToken: String, expectedClientId: String?): Mono<GoogleAuthResult> {
        return Mono.fromCallable { googleVerifier.verify(idToken, expectedClientId) }
            .flatMap { result ->
                val provider = "GOOGLE"
                identityRepo.findByProviderAndSubject(provider, result.subject)
                    .flatMap { identity ->
                        // Existing user
                        userRepo.findById(identity.userId)
                    }
                    .switchIfEmpty(
                        // Create new user + identity
                        Mono.defer {
                            val userId = "u-" + UUID.randomUUID().toString()
                            val user = UserEntity(
                                id = userId,
                                email = result.email,
                                firstName = result.firstName,
                                lastName = result.lastName,
                                country = result.country
                            )
                            userRepo.save(user).flatMap {
                                val identity = UserIdentityEntity(
                                    id = UUID.randomUUID().toString(),
                                    userId = userId,
                                    provider = provider,
                                    subject = result.subject
                                )
                                identityRepo.save(identity).thenReturn(user)
                            }
                        }
                    ).map { user ->
                        val token = jwtSupport.generate(user.id, provider = provider)
                        GoogleAuthResult(accessToken = token, expiresIn = jwtSupport.expiresInSeconds())
                    }
            }
    }
}
