package com.goodstadt.john.language.exams.models

import java.util.Date

//My user row
data class UserFirebase(
    val id: String, // Usually firebase Auth uid
    val UID: String, // Could be same as id
    val name: String, // Users given name
    val spokenName: String, // Name user is happy to hear spoken
    val loggedIn: Boolean,
    val isAnon: Boolean,
    val provider: String,
    val providerCompany: String,
    val email: String,
    val platform: String, //ios or android
    val version: String,
    val languageCode: String,
    val regionCode: String,
    val isEmailVerified: Boolean,
    val lastUpdateDate: Date,
    val lastLoggedInDate: Date,
    val lastLoggedOutDate: Date,
    val lastActivityDate: Date,

    val deviceManufacturer: String,
    val deviceModel: String,
    val deviceBrand: String,
    val deviceProduct: String,
    val deviceHardware: String,
    val deviceBoard: String,
    val createdAt: Date
)
