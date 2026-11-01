package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.model.*
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import kotlinx.serialization.json.*
import java.security.MessageDigest

object NativeApi {
    fun phone(value:String,country:String="86"):String {
        require(country.matches(Regex("[1-9][0-9]{0,3}"))){"请输入正确的国家区号"}
        val result=value.replace(Regex("[\\s-]"),"")
        require(result.matches(Regex(if(country=="86") "1[0-9]{10}" else "[0-9]{5,15}"))){"请输入正确的手机号码"}
        return result
    }
    suspend fun call(path:String,data:Map<String,Any> = emptyMap(),allowed:Set<Int> = setOf(200)):JsonObject {
        val result=json.parseToJsonElement(requestNetease(path,data)).jsonObject
        val code=result["code"]?.jsonPrimitive?.intOrNull ?: 200
        check(code in allowed){result["message"]?.jsonPrimitive?.contentOrNull ?: result["msg"]?.jsonPrimitive?.contentOrNull ?: if(code in setOf(301,302,401)) "请先登录" else "请求未完成（$code）"}
        return result
    }
    suspend fun sendCode(number:String,country:String="86") {
        val response=call("/login/captcha/send",mapOf("phone" to phone(number,country),"countrycode" to country))
        check(response["data"]?.jsonPrimitive?.booleanOrNull!=false){"验证码未发送，请稍后重试"}
    }
    suspend fun login(account:String,secret:String,password:Boolean,country:String="86"):UserInfoBatch {
        val fields=if(password) {
            require(secret.isNotEmpty()&&secret.length<=256){"请输入密码"}
            val hash=MessageDigest.getInstance("MD5").digest(secret.toByteArray()).joinToString(""){"%02x".format(it)}
            if('@' in account) mapOf("username" to account.trim(),"password" to hash) else mapOf("phone" to phone(account,country),"countrycode" to country,"password" to hash)
        } else {
            require(secret.matches(Regex("[0-9]{4,8}"))){"请输入短信验证码"}
            mapOf("phone" to phone(account,country),"countrycode" to country,"captcha" to secret)
        }
        val result=call(if(password) "/login/password" else "/login/captcha",fields)
        check(result["petrichorSessionEstablished"]?.jsonPrimitive?.booleanOrNull==true){"未取得有效登录凭据，请完成安全验证或使用网页登录"}
        return verifyLogin()
    }
    suspend fun verifyLogin():UserInfoBatch {
        check(CookieProvider.isLoggedIn()){"登录状态尚未生效"}
        return try { AccountApi.accountInfo().getOrThrow().also { check(it.account.profile.userId>0){"无法确认账号登录状态"} } }
        catch(error:Exception){CookieProvider.clear();throw error}
    }
    suspend fun createPlaylist(name:String,isPrivate:Boolean):Long {
        require(CookieProvider.isLoggedIn()){"请先登录"};require(name.trim().length in 1..40){"歌单名称应为 1–40 个字"}
        val result=call("/playlist/create",mapOf("name" to name.trim(),"privacy" to if(isPrivate) 10 else 0))
        return result["playlist"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: error("未返回新歌单")
    }
    fun trackIds(ids:List<Long>):String {require(ids.isNotEmpty()&&ids.all{it>0}){"请先选择歌曲"};return JsonArray(ids.distinct().map{JsonPrimitive(it)}).toString()}
    suspend fun manipulate(id:Long,ids:List<Long>,remove:Boolean) {
        require(CookieProvider.isLoggedIn()){"请先登录"};require(id>0);trackIds(ids)
        ids.distinct().chunked(100).forEach { chunk ->
            call("/playlist/manipulate",mapOf("pid" to id,"trackIds" to trackIds(chunk),"op" to if(remove) "del" else "add","imme" to "true"))
        }
    }
    suspend fun comments(thread:String,page:Int=1,sort:Int=99,cursor:String="-1"):JsonObject=call("/comment/list",mapOf("threadId" to thread,"pageNo" to page,"pageSize" to 20,"cursor" to (if(sort==3)cursor else if(sort==2)"normalHot#${(page-1)*20}" else "${(page-1)*20}"),"sortType" to sort,"showInner" to true))
    suspend fun postComment(thread:String,text:String,reply:Long=0) {
        require(CookieProvider.isLoggedIn()){"请先登录"};require(text.trim().length in 1..1000){"评论应为 1–1000 个字"}
        call("/comment/post",buildMap{put("threadId",thread);put("content",text.trim());if(reply>0)put("commentId",reply)})
    }
    suspend fun programSearch(query:String,offset:Int=0):List<ProgramMatch> {
        require(query.trim().isNotEmpty()){"请输入节目名称或节目中的歌曲"}
        val result=call("/search/programs",mapOf("keyword" to query.trim(),"limit" to 30,"offset" to offset))
        val data=result["data"]
        val array=when(data){is JsonArray->data;is JsonObject->data["resources"] as? JsonArray ?: data["list"] as? JsonArray;else->null} ?: JsonArray(emptyList())
        return array.mapNotNull { item ->
            val objectValue=item.jsonObject;val value=(objectValue["data"] as? JsonObject) ?: objectValue
            val program=(value["baseInfo"] as? JsonObject) ?: (value["program"] as? JsonObject) ?: value
            val id=program["id"]?.jsonPrimitive?.longOrNull ?: value["resourceId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val p=program
            val radio=(p["radio"] as? JsonObject)
            ProgramMatch(id,p["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),radio?.get("id")?.jsonPrimitive?.longOrNull ?: 0,radio?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }
}
data class ProgramMatch(val id:Long,val name:String,val radioId:Long,val radioName:String)
