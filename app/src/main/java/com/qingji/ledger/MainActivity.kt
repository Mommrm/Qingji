package com.qingji.ledger

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*

class MainActivity : ComponentActivity() {
    private var newEntrySignal by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);enableEdgeToEdge();if(intent.getBooleanExtra("new_entry",false))newEntrySignal++;setContent{LedgerRoot(newEntrySignal)} }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("new_entry",false))newEntrySignal++ }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerRoot(newEntrySignal:Int,vm:LedgerViewModel=viewModel()){
    val s by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current;val snack=remember{SnackbarHostState()}
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle();val restoreBusy by vm.restoreBusy.collectAsStateWithLifecycle()
    val restoreIsRecovery by vm.restoreIsRecovery.collectAsStateWithLifecycle();val restoreError by vm.restoreError.collectAsStateWithLifecycle()
    var page by rememberSaveable{mutableIntStateOf(0)};var editor by rememberSaveable{mutableStateOf<String?>(null)}
    var noteEntryId by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedEntryId by rememberSaveable{mutableStateOf<String?>(null)};var refundOriginalId by rememberSaveable{mutableStateOf<String?>(null)};var refundDraftId by rememberSaveable{mutableStateOf(newId())}
    var selectedGoalId by rememberSaveable{mutableStateOf<String?>(null)};var budgetMonthString by rememberSaveable{mutableStateOf(YearMonth.now().toString())};var budgetScope by rememberSaveable{mutableStateOf("")};var deposit by rememberSaveable{mutableStateOf(true)}
    var selectedAssetId by rememberSaveable{mutableStateOf<String?>(null)};var selectedTransferId by rememberSaveable{mutableStateOf<String?>(null)};var preferredTransferSource by rememberSaveable{mutableStateOf<String?>(null)}
    val editingAsset=s.assets.find{it.id==selectedAssetId};val editingTransfer=s.transfers.find{it.id==selectedTransferId}
    val budgetMonth=YearMonth.parse(budgetMonthString)
    val editingGoal=s.goals.find{it.id==selectedGoalId}
    val editingEntry=remember(s.entries,s.assets,selectedEntryId,refundOriginalId,refundDraftId){val original=s.entries.find{it.id==refundOriginalId};if(original!=null)Entry(id=refundDraftId,type=REFUND,amount=original.amount-s.live().filter{it.refundOf==original.id}.sumOf{it.amount},categoryId=original.categoryId,refundOf=original.id,assetId=original.assetId?.takeIf{id->s.assets.any{it.id==id&&!it.archived}}?:s.defaultAssetId(),affectsBalance=true)else s.entries.find{it.id==selectedEntryId}}
    var csvStart by rememberSaveable{mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())};var csvEnd by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    val csvRange=DateRange(LocalDate.parse(csvStart),LocalDate.parse(csvEnd))
    var deleteGoal by remember{mutableStateOf<Goal?>(null)}
    LaunchedEffect(vm){vm.notices.collect{notice->launch{val result=snack.showSnackbar(notice.message,actionLabel=if(notice.undo!=null)"撤销" else null,duration=if(notice.undo!=null)SnackbarDuration.Long else SnackbarDuration.Short);if(result==SnackbarResult.ActionPerformed)notice.undo?.let{undo->vm.action("已撤销"){undo()}}}}}
    LaunchedEffect(newEntrySignal){if(newEntrySignal>0){selectedEntryId=null;refundOriginalId=null;editor="entry"}}
    val enablePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.updateSettings{it.copy(enabled=true)}else vm.notify("未开启通知权限，记账与分析仍可使用")}
    val exportBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)vm.action("完整备份已保存"){BackupFiles.write(context,uri,BackupCodec.encode(vm.repo.snapshot()))}}
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)vm.action("CSV 已导出"){BackupFiles.write(context,uri,BackupCodec.csv(vm.repo.snapshot(),csvRange))}}
    val importBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.action{val decoded=try{BackupFiles.read(context,uri)}catch(e:Exception){if(e is CancellationException)throw e;throw IllegalArgumentException("备份文件无效或已损坏："+(e.message?:"请检查文件内容"))};vm.proposeRestore(decoded,false)}}
    LedgerTheme(s.settings.theme,s.settings.accent){
        val addEntry={selectedEntryId=null;refundOriginalId=null;editor="entry"}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snack)},topBar={
            Surface(color=if(page==0)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,contentColor=if(page==0)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface){Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){
                if(page==3)IconButton(onClick={page=4}){Icon(Icons.Outlined.ArrowBack,"返回我的")}
                Text(listOf("轻记","收支图表","资产与负债","预算与攒钱","我的")[page],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                if(page==0)Text("认真生活，轻松记账",style=MaterialTheme.typography.labelSmall)
            }}
        },bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
            val routes=listOf(0,1,-1,2,4);val icons=listOf(Icons.Outlined.ReceiptLong,Icons.Outlined.BarChart,Icons.Outlined.Add,Icons.Outlined.AccountBalanceWallet,Icons.Outlined.PersonOutline);val labels=listOf("明细","图表","记账","资产","我的")
            routes.forEachIndexed{i,route->if(route<0){Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){FilledIconButton(onClick=addEntry,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer)){Icon(Icons.Outlined.Add,"记一笔",Modifier.size(30.dp))};Text("记账",style=MaterialTheme.typography.labelSmall)}}else NavigationBarItem(selected=page==route||(route==4&&page==3),onClick={page=route},icon={Icon(icons[i],labels[i])},label={Text(labels[i])})}
        }}){padding->Box(Modifier.fillMaxSize().padding(padding)){
            if(!s.ready)CircularProgressIndicator(Modifier.align(Alignment.Center)) else when(page){
                0->HomeScreen(s,{selectedEntryId=it.id;refundOriginalId=null;editor="entry"},{month->budgetMonthString=month.toString();budgetScope="";editor="budget"},{page=2},{vm.delete(it)},{noteEntryId=it.id})
                1->AnalysisScreen(s,{selectedEntryId=it.id;refundOriginalId=null;editor="entry"}){range->csvStart=range.start.toString();csvEnd=range.end.toString();exportCsv.launch("轻记-收支-${range.start}-${range.end}.csv")}
                2->AssetsScreen(s,{selectedAssetId=null;editor="asset_edit"},{asset->selectedAssetId=asset.id;editor="asset_details"},{selectedTransferId=null;preferredTransferSource=null;editor="transfer"})
                3->PlansScreen(s,{month,category->budgetMonthString=month.toString();budgetScope=category;editor="budget"},{goal->selectedGoalId=goal?.id;editor="goal"},{goal,isDeposit->selectedGoalId=goal.id;deposit=isDeposit;editor="progress"},{goal->selectedGoalId=goal.id;editor="history"},{goal->deleteGoal=goal},initialBudget=true)
                4->SettingsScreen(s,vm::updateSettings,{enable->if(!enable)vm.updateSettings{it.copy(enabled=false)}else if(Build.VERSION.SDK_INT>=33&&!ReminderScheduler.allowed(context))enablePermission.launch(Manifest.permission.POST_NOTIFICATIONS)else vm.updateSettings{it.copy(enabled=true)}},{editor="categories"},{exportBackup.launch("轻记-完整备份-${LocalDate.now()}.json")},{importBackup.launch(arrayOf("application/json","text/plain","application/octet-stream"))},{vm.action{val decoded=BackupFiles.readRecovery(context);vm.proposeRestore(decoded,true)}},{page=3},{editor="trash"})
            }
        }}
        val dismiss={editor=null}
        when(editor){
            "entry"->if(s.ready)EntryEditor(editingEntry,s,vm,dismiss){original->refundOriginalId=original.id;refundDraftId=newId();selectedEntryId=null;editor="entry"}
            "budget"->BudgetEditor(budgetMonth,budgetScope,s,vm,dismiss)
            "goal"->GoalEditor(editingGoal,vm,dismiss)
            "progress"->editingGoal?.let{ProgressEditor(it,deposit,s,vm,dismiss)}
            "history"->editingGoal?.let{GoalHistory(it,s,dismiss)}
            "categories"->CategoryManager(s,vm,dismiss)
            "trash"->TrashSheet(s,vm,dismiss)
            "asset_edit"->AssetEditor(editingAsset,s,vm,dismiss)
            "asset_calibrate"->editingAsset?.let{CalibrationEditor(it,s,vm,dismiss)}
            "transfer"->TransferEditor(editingTransfer,preferredTransferSource,s,vm,dismiss)
            "asset_details"->editingAsset?.let{asset->AssetDetails(asset,s,vm,dismiss,{editor="asset_edit"},{editor="asset_calibrate"},{source->selectedTransferId=null;preferredTransferSource=source;editor="transfer"},{entry->selectedEntryId=entry.id;refundOriginalId=null;editor="entry"},{transfer->selectedTransferId=transfer.id;editor="transfer"})}
        }
        noteEntryId?.let{id->s.live().find{it.id==id}?.let{EntryNoteDialog(it,vm){noteEntryId=null}}}
        pendingRestore?.let{backup->AlertDialog(onDismissRequest={vm.cancelRestore()},title={Text(if(restoreIsRecovery)"恢复导入前快照？" else "确认恢复备份？")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("备份包含：\n${backup.entries.count{it.deletedAt==null}}笔收支\n${backup.assets.size}个账户\n${backup.transfers.count{it.deletedAt==null}}笔转账\n${backup.categories.size}个分类\n${backup.goals.size}个攒钱目标\n${backup.budgets.size}项预算");Text("恢复将替换当前账本与资产。会先保存当前数据快照；恢复后提醒默认关闭。重复恢复同一文件不会重复入账。");if(backup.assets.isEmpty())Hint("此备份不含资产账户；恢复后旧账不会关联或扣减资产。");restoreError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(enabled=!restoreBusy,onClick={vm.performRestore()}){Text(if(restoreBusy)"正在恢复…" else "确认替换")}},dismissButton={TextButton(enabled=!restoreBusy,onClick={vm.cancelRestore()}){Text("取消")}})}
        deleteGoal?.let{goal->AlertDialog(onDismissRequest={deleteGoal=null},title={Text("删除这个攒钱目标？")},text={Text("将同时删除“${goal.title}”的进度历史。删除后可在短时提示中撤销。")},confirmButton={TextButton(onClick={vm.removeGoal(goal);deleteGoal=null}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleteGoal=null}){Text("取消")}})}
    }
}
