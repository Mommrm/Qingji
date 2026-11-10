@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingji.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable fun FastInputSheet(title:String,onDismiss:()->Unit,form:@Composable ColumnScope.()->Unit,footer:@Composable ColumnScope.()->Unit){
    val max=LocalConfiguration.current.screenHeightDp.dp*.90f
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),dragHandle={}){
        Column(Modifier.fillMaxWidth().heightIn(max=max).navigationBarsPadding().imePadding().padding(horizontal=16.dp)){
            Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"关闭")}}
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp),content=form)
            Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(6.dp),content=footer)
        }
    }
}
@Composable fun AmountDisplay(amount:String,onClear:()->Unit){
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(14.dp)){
        Row(Modifier.fillMaxWidth().padding(start=15.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){
            Text("¥",fontSize=22.sp);Text(amount.ifEmpty{"0.00"},Modifier.weight(1f).padding(start=10.dp),fontSize=32.sp,fontWeight=FontWeight.Medium,maxLines=1)
            IconButton(onClick=onClear){Icon(Icons.Outlined.Close,"清空金额",Modifier.size(19.dp))}
        }
    }
}
@Composable fun TextInputDialog(title:String,value:String,max:Int,onDismiss:()->Unit,onSave:(String)->Unit){
    var draft by remember(value){mutableStateOf(value)};val keyboard=LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest={keyboard?.hide();onDismiss()},title={Text(title)},text={OutlinedTextField(draft,{if(it.length<=max)draft=it},Modifier.fillMaxWidth(),singleLine=max<=30,maxLines=if(max<=30)1 else 4,label={Text(title)},supportingText={if(max>30)Text("${draft.length}/$max")})},confirmButton={TextButton(onClick={keyboard?.hide();onSave(draft);onDismiss()}){Text("完成")}},dismissButton={TextButton(onClick={keyboard?.hide();onDismiss()}){Text("取消")}})
}
@Composable fun NoteAction(note:String,onChange:(String)->Unit){
    var show by remember{mutableStateOf(false)}
    TextButton(onClick={show=true}){Icon(Icons.Outlined.EditNote,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(note.ifBlank{"备注"}.take(14),maxLines=1)}
    if(show)TextInputDialog("备注",note,500,{show=false},onChange)
}
@Composable fun AssetKindPicker(kind:String,onSelect:(String)->Unit){
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(2.dp),maxItemsInEachRow=5){
        ASSET_KINDS.forEach{(id,label)->Column(Modifier.width(60.dp).clickable{onSelect(id)}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
            Box{AssetBadge(id,42.dp);if(kind==id)Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.align(Alignment.BottomEnd).offset(4.dp,4.dp)){Icon(Icons.Outlined.Check,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}}
            Text(label,fontSize=11.sp,maxLines=1,fontWeight=if(id==kind)FontWeight.Bold else FontWeight.Normal,color=if(id==kind)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }}
    }
}
