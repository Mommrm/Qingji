package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

class FastInputTest{
    @Test fun namesAutofillAndAvoidActiveDuplicates(){
        val existing=listOf(AssetAccount(name="微信",kind="WECHAT"),AssetAccount(name="微信 2",kind="WECHAT"),AssetAccount(name="微信 3",kind="WECHAT",archived=true))
        assertEquals("微信 3",suggestedAssetName("WECHAT",existing))
        assertEquals("支付宝",suggestedAssetName("ALIPAY",existing))
        assertEquals("银行卡",suggestedAssetName("BANK",emptyList()))
    }
    @Test fun zeroBalanceAllowedButZeroTransactionRejected(){
        var value="";listOf("0",".","0","0").forEach{value=keypadInput(value,it)}
        assertEquals(0L,parseBalance(value));assertNull(keypadMoney(value))
        value=keypadInput(value,"DEL");value=keypadInput(value,"5")
        assertEquals(5L,parseBalance(value));assertEquals(5L,keypadMoney(value));assertNull(parseBalance(""))
    }
    @Test fun assetChartUsesOnlyActivePositiveBalances(){
        val a=AssetAccount(id="a",name="微信",kind="WECHAT",openingBalance=10000)
        val b=AssetAccount(id="b",name="支付宝",kind="ALIPAY",openingBalance=30000)
        val c=AssetAccount(id="c",name="已归档",kind="BANK",openingBalance=99999,archived=true)
        val d=AssetAccount(id="d",name="现金",kind="CASH",openingBalance=0)
        val s=Snapshot(assets=listOf(a,b,c,d),entries=listOf(Entry(type=EXPENSE,amount=1000,categoryId="x",assetId=d.id)))
        val shares=chartShares(s.assetSlices())
        assertEquals(listOf("ALIPAY","WECHAT"),shares.map{it.first.id})
        assertEquals(.75f,shares[0].second,.0001f);assertEquals(.25f,shares[1].second,.0001f)
        assertEquals(40000L,s.assetTotal())           // 总资产只累加正余额
        assertEquals(0L,s.assetDebtTotal())            // 资产类账户的负余额不算负债，负债只看负债类账户
        assertEquals(-1000L,s.assetBalances()[d.id])
    }
    @Test fun emptyAndInvalidChartValuesNeverProduceInvalidPercentages(){
        assertTrue(chartShares(emptyList()).isEmpty())
        assertTrue(chartShares(listOf(ChartSlice("x","x",0.0),ChartSlice("y","y",-1.0),ChartSlice("z","z",Double.NaN))).isEmpty())
        val s=chartShares(listOf(ChartSlice("a","a",20.0),ChartSlice("b","b",30.0)))
        assertEquals(1f,s.sumOf{it.second.toDouble()}.toFloat(),.00001f)
    }
}
