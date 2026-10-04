package dev.dots.room

data class ControlState(val canSend:Boolean=false,val canSync:Boolean=false,val canText:Boolean=false,val maxTextLength:Int=2000,val sending:Boolean=false,val syncing:Boolean=false,val message:String="",val lastSync:String?=null){val busy:Boolean get()=sending||syncing}
class ControlFailure(val code:String):Exception(code)
enum class PresetOption(val id:String,val text:String){BRIEFING("briefing","지금 뭐하고 있어? 브리핑해줘"),THANKS("thanks","고마워"),STOP("stop","멈춰"),LATER("later","알겠어. 이따 확인해 볼게"),CONTINUE("continue","계속해줘")}
fun controlError(code:String?):String=when(code){
 "DRAFT_EXISTS","DRAFT_CHANGED"->"브라우저에 작성 중인 메시지가 있어요. 먼저 확인해 주세요."
 "USER_CONTROL"->"브라우저가 사용자 제어 중이에요."
 "ADDRESS_CHANGED"->"지정한 바미 대화 페이지를 확인해 주세요."
 "UNSUPPORTED_OR_LOGIN","UNSUPPORTED_EDITOR"->"브라우저 로그인과 대화 화면을 확인해 주세요."
 "BUSY"->"다른 요청을 처리하고 있어요. 잠시 후 다시 눌러 주세요."
 "UNAUTHORIZED"->"페어링 자격 증명을 확인해 주세요."
 "INVALID_TEXT","INVALID_REQUEST"->"메시지를 1~2,000자로 입력해 주세요."
 "NOT_SUPPORTED"->"이 브리지는 해당 기능을 지원하지 않아요. 업데이트를 확인해 주세요."
 "SEND_DISABLED"->"현재 브라우저에서 메시지를 보낼 수 없어요."
 else->"브라우저와 USB 연결을 확인하고 다시 눌러 주세요."
}
