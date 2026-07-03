package com.example.data.model

data class Offer(
    val title: String,
    val price: String,
    val description: String,
    val validity: String,
    val ussdCode: String,
    val operatorName: String,
    val category: String // "Internet" or "Appel"
)
