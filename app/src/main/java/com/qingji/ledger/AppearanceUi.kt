package com.qingji.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

fun categoryVector(name:String):ImageVector=when {
    name.contains("餐")||name.contains("食")->Icons.Outlined.Restaurant
    name.contains("购物")||name.contains("日用")->Icons.Outlined.ShoppingBag
    name.contains("交通")->Icons.Outlined.DirectionsBus
    name.contains("住")||name.contains("房")->Icons.Outlined.Home
    name.contains("医")||name.contains("健康")->Icons.Outlined.LocalHospital
    name.contains("娱")->Icons.Outlined.SportsEsports
    name.contains("学习")||name.contains("教育")->Icons.Outlined.School
    name.contains("工资")->Icons.Outlined.WorkOutline
    name.contains("红包")||name.contains("礼")->Icons.Outlined.Redeem
    name.contains("投资")||name.contains("理财")->Icons.Outlined.TrendingUp
    name.contains("旅行")->Icons.Outlined.Flight
    name.contains("运动")->Icons.Outlined.FitnessCenter
    else->Icons.Outlined.Category
}
@Composable fun CategoryBadge(name:String,selected:Boolean=false){
    Surface(shape=CircleShape,color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,contentColor=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface){Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){Icon(categoryVector(name),null,Modifier.size(23.dp))}}
}
@Composable fun CategoryGrid(categories:List<Category>,selectedId:String,onSelect:(String)->Unit){
    categories.chunked(5).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        row.forEach{c->Column(Modifier.weight(1f).clickable{onSelect(c.id)}.semantics{selected=c.id==selectedId}.padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){CategoryBadge(c.name,c.id==selectedId);Text(c.name,fontSize=11.sp,maxLines=1,color=if(c.id==selectedId)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}
        repeat(5-row.size){Spacer(Modifier.weight(1f))}
    }}
}
@Composable fun AmountKeypad(onKey:(String)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
        listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf(".","0","DEL")).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach{key->
            Surface(onClick={onKey(key)},modifier=Modifier.weight(1f).height(48.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Box(contentAlignment=Alignment.Center){if(key=="DEL")Icon(Icons.Outlined.Backspace,"删除一位")else Text(key,fontSize=23.sp)}}
        }}}
    }
}
@Composable fun CompactAssetChoice(title:String,s:Snapshot,selectedId:String?,onSelect:(String?)->Unit,includeNone:Boolean=true,pool:String?=null){
    var expanded by remember{mutableStateOf(false)}
    val asset=s.assets.find{it.id==selectedId}
    val balances=remember(s.assets,s.entries,s.transfers,s.adjustments){s.assetBalances()}
    Box{
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
            AssetBadge(asset?.kind?:"OTHER",24.dp);Spacer(Modifier.width(8.dp))
            Text(asset?.name?:title,Modifier.weight(1f),maxLines=1)
            Icon(Icons.Outlined.ExpandMore,"选择账户")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=380.dp)){
            if(includeNone)DropdownMenuItem(text={Text("不关联资产")},onClick={onSelect(null);expanded=false})
            val options=s.assets.filter{(!it.archived||it.id==selectedId)&&(pool==null||normalizePool(it.pool)==pool)}
            val assetOptions=options.filter{!isDebtKind(it.kind)}
            val debtOptions=options.filter{isDebtKind(it.kind)}
            if(assetOptions.isNotEmpty()){
                Text("资产账户",Modifier.padding(start=12.dp,top=6.dp,bottom=2.dp),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                assetOptions.forEach{a->DropdownMenuItem(leadingIcon={AssetBadge(a.kind,28.dp)},text={Column{Text(a.name+if(a.archived)"（已归档）" else "",maxLines=1);Hint(poolLabel(a.pool)+" · 余额 ¥"+money(balances[a.id]?:0))}},onClick={onSelect(a.id);expanded=false})}
            }
            if(debtOptions.isNotEmpty()){
                if(assetOptions.isNotEmpty())HorizontalDivider()
                Text("负债账户",Modifier.padding(start=12.dp,top=6.dp,bottom=2.dp),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.error)
                debtOptions.forEach{a->val bal=balances[a.id]?:0L;DropdownMenuItem(leadingIcon={AssetBadge(a.kind,28.dp)},text={Column{Text(a.name+if(a.archived)"（已归档）" else "",maxLines=1);Text(if(bal<0)"欠款 ¥"+money(-bal) else "已结清 · 溢缴 ¥"+money(bal),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}},onClick={onSelect(a.id);expanded=false})}
            }
        }
    }
}
@Composable fun AppearancePicker(settings:Settings,onSettings:((Settings)->Settings)->Unit){
    var custom by rememberSaveable{mutableStateOf(if(settings.accent.startsWith("#"))settings.accent else "#FFDA63")}
    var showCustom by rememberSaveable{mutableStateOf(false)}
    Panel{
        Heading("主题与外观")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){ACCENT_OPTIONS.forEach{option->
            Column(Modifier.weight(1f).clickable{onSettings{it.copy(accent=option.id)}}.semantics{contentDescription=option.title;selected=settings.accent==option.id}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
                Surface(shape=CircleShape,color=composeColor(rgb(option.hex)),modifier=Modifier.size(40.dp)){Box(contentAlignment=Alignment.Center){if(settings.accent==option.id)Icon(Icons.Outlined.Check,null,tint=composeColor(readableInk(rgb(option.hex))))}}
                Text(option.title.take(2),fontSize=12.sp)
            }
        }}
        TextButton(onClick={showCustom=!showCustom}){Text(if(settings.accent.startsWith("#"))"自定义颜色 · ${settings.accent}" else "自定义主题色")}
        if(showCustom){
            OutlinedTextField(custom,{if(it.length<=7)custom=it.uppercase()},Modifier.fillMaxWidth(),singleLine=true,label={Text("颜色代码，如 #FFDA63")},isError=custom.isNotEmpty()&&!custom.matches(Regex("#[0-9A-Fa-f]{6}")))
            Button(onClick={onSettings{it.copy(accent=custom.uppercase())}},enabled=custom.matches(Regex("#[0-9A-Fa-f]{6}"))){Text("应用颜色")}
        }
        ChoiceRow(listOf("跟随系统","浅色","深色"),listOf("SYSTEM","LIGHT","DARK").indexOf(settings.theme)){index->onSettings{it.copy(theme=listOf("SYSTEM","LIGHT","DARK")[index])}}
    }
}
