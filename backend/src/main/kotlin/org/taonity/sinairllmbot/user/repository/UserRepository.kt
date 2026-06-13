package org.taonity.sinairllmbot.user.repository

import org.taonity.sinairllmbot.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, String>
