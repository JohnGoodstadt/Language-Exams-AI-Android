package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//import com.google.gson.annotations.SerializedName
//import kotlinx.serialization.Serializable

@Serializable
data class TestMyselfListRoot(
    @SerialName("fileformat") val fileFormat: Int, //case sensitive
    @SerialName("sheetname") var sheetName: String,
    val title: String? = null,//"", //read title for localised versions
    val updatedDate: Long,
    val location: Int,
    val data: List<TestMyselfList>
)
@Serializable
data class TestMyselfList(
    val title: String,
    val description: String,
    val sortorder: Int,
    val learningTitle: String? = null,
    val learningPoints: List<String> = emptyList(),
    var sections: List<TestMyselfSections>
)
@Serializable
data class TestMyselfSections(
    val title: String,
    var page: Int,
    val sentence: String,
    val explain: String,
    val summary: String,
    // CEFR band for this question ("A2", "B1", "B2"). Only the baseline audit uses it,
    // to place the learner by band performance; null/absent for all other content.
    val level: String? = null,
    var words: List<TestMyselfWordsState>
)
@Serializable
data class TestMyselfWordsState(
    val word: String,
    val ok: Boolean
)
