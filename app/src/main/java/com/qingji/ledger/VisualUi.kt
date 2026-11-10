package com.qingji.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@Composable fun InfoHint(title:String,detail:String){
    var show by remember{mutableStateOf(false)}
    IconButton(onClick={show=true},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.Info,title,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text(title)},text={Text(detail)},confirmButton={TextButton(onClick={show=false}){Text("知道了")}})
}
@Composable fun AssetBadge(kind:String,side:Dp=40.dp){
    val brand=kind=="WECHAT"||kind=="ALIPAY"
    val bg=when(kind){"WECHAT"->Color(0xFF07C160);"ALIPAY"->Color.White;"BANK"->MaterialTheme.colorScheme.surfaceContainer;"CASH"->Color(0xFFE9F5EC);"CREDIT_CARD"->Color(0xFFFDEDE6);"HUABEI"->Color(0xFFE7F1FF);"CREDIT"->Color(0xFFF1EBFA);else->MaterialTheme.colorScheme.surfaceContainer}
    Surface(shape=RoundedCornerShape(side*.28f),color=bg,modifier=Modifier.size(side)){
        Box(contentAlignment=Alignment.Center){
            if(brand)Icon(painterResource(if(kind=="WECHAT")R.drawable.ic_wechat else R.drawable.ic_alipay),ASSET_KINDS[kind],Modifier.size(side*.7f),tint=Color.Unspecified)
            else Icon(when(kind){"BANK"->Icons.Outlined.AccountBalance;"CASH"->Icons.Outlined.Payments;"CREDIT_CARD","HUABEI","CREDIT"->Icons.Outlined.CreditCard;else->Icons.Outlined.AccountBalanceWallet},ASSET_KINDS[kind]?:"其他",Modifier.size(side*.58f),tint=if(kind=="CASH")Color(0xFF287747)else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun ToolRow(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=50.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun sliceColor(index:Int):Color{
    val colors=listOf(MaterialTheme.colorScheme.primary,Color(0xFF4B91D1),Color(0xFF5BA584),Color(0xFFAB81C5),Color(0xFFE1A44D),Color(0xFFD47A91))
    return colors[index%colors.size]
}
@Composable fun RingChart(slices:List<ChartSlice>,label:String,center:String="",modifier:Modifier=Modifier){
    val shares=chartShares(slices);val colors=shares.indices.map{sliceColor(it)};val track=MaterialTheme.colorScheme.surfaceContainer
    Box(modifier.size(132.dp).semantics{contentDescription=label+if(shares.isEmpty())"，暂无数据" else shares.joinToString{"，${it.first.label} ${(it.second*100).roundToInt()}%"}},contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize().padding(6.dp)){
            val stroke=15.dp.toPx();val bounds=Size(size.width-stroke,size.height-stroke);val origin=Offset(stroke/2,stroke/2)
            drawArc(track,0f,360f,false,origin,bounds,style=Stroke(stroke))
            var angle=-90f
            shares.forEachIndexed{i,(_,share)->val sweep=360f*share;drawArc(colors[i],angle,if(shares.size==1)sweep else (sweep-2f).coerceAtLeast(.1f),false,origin,bounds,style=Stroke(stroke));angle+=sweep}
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){if(center.isNotBlank())Text(center,fontSize=21.sp,fontWeight=FontWeight.SemiBold);Text(if(shares.isEmpty())"暂无数据" else label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable fun AssetDistribution(s:Snapshot){
    val slices=remember(s.assets,s.entries,s.transfers,s.adjustments){s.assetSlices()};val shares=chartShares(slices)
    val negative=s.assets.count{!it.archived&&(s.assetBalances()[it.id]?:0)<0}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
        RingChart(slices,if(negative>0)"正余额分布" else "资产分布")
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
            shares.forEachIndexed{i,(slice,share)->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Surface(shape=CircleShape,color=sliceColor(i),modifier=Modifier.size(7.dp)){}
                AssetBadge(slice.id,20.dp);Text(slice.label,Modifier.weight(1f),fontSize=12.sp);Text("${(share*100).roundToInt()}%",fontSize=12.sp)
            }}
        }
    }
    if(negative>0)Text("$negative 个账户余额为负",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
}
@Composable fun BudgetRing(spent:Long,limit:Long){
    val fraction=if(limit>0)(spent.toDouble()/limit).coerceIn(0.0,1.0).toFloat() else 0f
    val color=if(spent>=limit)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.size(76.dp).semantics{contentDescription="预算使用 ${if(limit>0)(spent.toDouble()/limit*100).toInt() else 0}%"},contentAlignment=Alignment.Center){
        CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),color=color,trackColor=MaterialTheme.colorScheme.surfaceContainer,strokeWidth=7.dp)
        Text("${if(limit>0)(spent.toDouble()/limit*100).toInt() else 0}%",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    }
}
