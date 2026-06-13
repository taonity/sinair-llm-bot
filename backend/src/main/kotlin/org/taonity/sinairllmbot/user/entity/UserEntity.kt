package org.taonity.sinairllmbot.user.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "app_user")
class UserEntity(
    @Id
    val googleId: String,
    var email: String,
    var displayName: String,
    var pictureUrl: String? = null
) {
    fun updateDetails(displayName: String, email: String, pictureUrl: String?): UserEntity {
        this.displayName = displayName
        this.email = email
        this.pictureUrl = pictureUrl
        return this
    }

    override fun hashCode(): Int = googleId.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserEntity
        return googleId == other.googleId
    }

    override fun toString(): String {
        return "UserEntity(googleId='$googleId', email='$email', displayName='$displayName')"
    }
}
