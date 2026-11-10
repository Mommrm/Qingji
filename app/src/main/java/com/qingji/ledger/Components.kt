package com.qingji.ledger

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.*

fun composeColor(value:Int)=Color(0xFF000000L or value.toLong())
@Composable fun LedgerTheme(theme: String, accent:String="SUN", content: @Composable () -> Unit) {
    val dark=when(theme){"DARK"->true;"LIGHT"->false;else->isSystemInDarkTheme()}
    val view=LocalView.current
    SideEffect { (view.context as? Activity)?.let { activity -> WindowCompat.getInsetsController(activity.window,view).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark } } }
    val p=paletteColors(accent,dark)
    val colors=(if(dark)darkColorScheme() else lightColorScheme()).copy(primary=composeColor(p.primary),onPrimary=composeColor(p.onPrimary),primaryContainer=composeColor(p.container),onPrimaryContainer=composeColor(p.onContainer),secondary=composeColor(p.primary),onSecondary=composeColor(p.onPrimary),secondaryContainer=composeColor(p.soft),onSecondaryContainer=if(dark)Color(0xFFE7E9EE)else Color(0xFF202124),background=composeColor(p.background),surface=composeColor(p.surface),surfaceContainerLow=composeColor(p.surface),surfaceContainer=composeColor(p.soft),surfaceContainerHigh=composeColor(p.soft),surfaceContainerHighest=composeColor(p.soft),surfaceVariant=composeColor(p.soft))
    MaterialTheme(colorScheme=colors,typography=Typography(),content=content)
}
@Composable fun Panel(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
}
@Composable fun Hint(text: String, modifier: Modifier=Modifier) { Text(text,modifier,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
@Composable fun Heading(title: String, action: String?=null, onAction:()->Unit={}) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(action!=null)TextButton(onClick=onAction){Text(action)} } }
@Composable fun MoneyText(value: Long, modifier: Modifier=Modifier, size: TextUnit=28.sp, color: Color=MaterialTheme.colorScheme.onSurface) { Text("¥ ${money(value)}",modifier,fontFamily=FontFamily.SansSerif,fontSize=size,color=color,maxLines=2) }
@Composable fun EmptyState(title: String="还没有记录", description: String="点击下方＋，记下第一笔收支。", action: String?=null,onAction:()->Unit={}) { Column(Modifier.fillMaxWidth().padding(vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Text("○",fontSize=34.sp,color=MaterialTheme.colorScheme.primary);Text(title,fontWeight=FontWeight.Medium);Hint(description);if(action!=null)TextButton(onClick=onAction){Text(action)}} }
@Composable fun EntryRow(entry: Entry, snapshot: Snapshot, onClick:()->Unit) {
    val cat=snapshot.category(entry.categoryId)
    val budgetTag=if(entry.repayment)" · 还款" else if(!entry.inBudget)" · 不计预算" else ""
    val poolTag=if(normalizePool(entry.pool)==POOL_INVEST)" · 理财" else ""
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=13.dp).semantics { contentDescription="${entry.date} ${cat?.name} ${when(entry.type){INCOME->"收入";REFUND->"退款";else->"支出"}} ${money(entry.amount)}元 ${entry.note.take(60)}$poolTag$budgetTag" },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        CategoryBadge(cat?.name?:"其他")
        Column(Modifier.weight(1f)){
            Text((cat?.name?:"已归档分类")+(if(entry.type==REFUND)" · 退款" else "")+poolTag+budgetTag,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${entry.date.substring(5)} ${entry.time} · ${entry.note.ifBlank { "无备注" }}",modifier=Modifier.padding(top=3.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            entry.assetId?.let{id->snapshot.assets.find{it.id==id}?.let{asset->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){AssetBadge(asset.kind,14.dp);Text(asset.name+if(entry.affectsBalance)"" else " · 仅关联",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}}}
        }
        Text((if(entry.type==EXPENSE)"−" else "+")+money(entry.amount),fontFamily=FontFamily.SansSerif,fontSize=18.sp,color=if(entry.type==INCOME || entry.type==REFUND)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,modifier=Modifier.widthIn(max=150.dp))
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
}
@Composable fun ChoiceRow(labels: List<String>, selected: Int, onSelect:(Int)->Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) { labels.forEachIndexed { index,label -> FilterChip(selected==index,onClick={onSelect(index)},label={Text(label)}) } } }
@Composable fun MonthPicker(month: YearMonth, onChange:(YearMonth)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${month.year} 年 ${month.monthValue} 月",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton(onClick={onChange(month.minusMonths(1))}){Text("‹",fontSize=25.sp)};TextButton(onClick={onChange(month.plusMonths(1))},enabled=month<YearMonth.now()){Text("›",fontSize=25.sp)}} }
@Composable fun Progress(value: Float, over: Boolean=false) { LinearProgressIndicator(progress={value.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),color=if(over)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surfaceContainer) }
fun pickDate(context: Context, initial: LocalDate=LocalDate.now(), maxToday: Boolean=true, onResult:(LocalDate)->Unit) { DatePickerDialog(context,{_,y,m,d->onResult(LocalDate.of(y,m+1,d))},initial.year,initial.monthValue-1,initial.dayOfMonth).apply{if(maxToday)datePicker.maxDate=System.currentTimeMillis()}.show() }
fun pickTime(context: Context, initial: String, onResult:(String)->Unit) { val time=LocalTime.parse(initial);TimePickerDialog(context,{_,h,m->onResult(LocalTime.of(h,m).toString())},time.hour,time.minute,true).show() }
