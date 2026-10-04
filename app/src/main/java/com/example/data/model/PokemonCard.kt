package com.example.data.model

data class PokemonCard(
  val id: String,
  val name: String,
  val pack: BoosterPack,
  val rarity: CardRarity,
  val hp: Int,
  val type: String,
  val attackName: String,
  val attackDamage: String,
  val isEx: Boolean = false,
  val isFullArt: Boolean = false,
  val isSecretRare: Boolean = false,
  val isImmersive: Boolean = false,
  val rulesName: String = name,
  val category: String = "unknown",
  val stage: String = "unknown",
  val evolvesFrom: String = "",
  val packNames: List<String> = emptyList(),
  val source: String = "Datos heredados"
)
