package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//import com.google.gson.annotations.SerializedName
//import kotlinx.serialization.Serializable

@Serializable
data class TestMyselfListRoot(
    @SerialName("fileformat") val fileFormat: Int, //case sensitive
    @SerialName("sheetname") var sheetName: String,
    val updatedDate: Long,
    val location: Int,
    val data: List<TestMyselfList>
)
@Serializable
data class TestMyselfList(
    val title: String,
    val description: String,
    val sortorder: Int,
    var sections: List<TestMyselfSections>
)
@Serializable
data class TestMyselfSections(
    val title: String,
    var page: Int,
    val sentence: String,
    val explain: String,
    val summary: String,
    var words: List<TestMyselfWordsState>
)
@Serializable
data class TestMyselfWordsState(
    val word: String,
    val ok: Boolean
)
