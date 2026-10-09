package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Shared visual language: deep blue panels, fine borders, and restrained light. */
@Composable
fun DexPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.Transparent, contentColor = colors.onSurface,
    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .7f)), shadowElevation = 3.dp) {
    Column(Modifier.background(Brush.linearGradient(listOf(colors.surfaceContainerHigh.copy(alpha = .65f), colors.surface))), content = content)
  }
}

@Composable
fun DexBall(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val r = size.minDimension / 2
    drawCircle(Color(0xFFF5FAFF),r)
    drawArc(Color(0xFFF34A55),180f,180f,true)
    drawLine(Color(0xFF07142B),Offset(0f,r),Offset(size.width,r),r*.2f)
    drawCircle(Color(0xFF07142B),r*.34f)
    drawCircle(Color(0xFFF5FAFF),r*.21f)
  }
}

@Composable
fun DexRarityIcon(kind: Int, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w=size.width;val h=size.height
    val path=Path()
    val gold=Color(0xFFFFC857)
    when(kind) {
      0 -> {
        path.moveTo(w*.5f,0f);path.lineTo(w*.95f,h*.5f);path.lineTo(w*.5f,h);path.lineTo(w*.05f,h*.5f);path.close()
        drawPath(path,Brush.linearGradient(listOf(Color(0xFFE8FAFF),Color(0xFF719DC6))))
        val facet=Path().apply {moveTo(w*.5f,0f);lineTo(w*.5f,h);lineTo(w*.05f,h*.5f);close()}
        drawPath(facet,Color.White.copy(alpha=.4f))
        drawLine(Color.White.copy(alpha=.7f),Offset(w*.05f,h*.5f),Offset(w*.95f,h*.5f),w*.035f)
      }
      1 -> {
        for(i in 0..9) {
          val angle=-PI/2+i*PI/5;val radius=if(i%2==0) w*.5f else w*.22f
          val x=w/2+cos(angle).toFloat()*radius;val y=h/2+sin(angle).toFloat()*radius
          if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        };path.close();drawPath(path,Brush.linearGradient(listOf(Color(0xFFFFEE99),gold,Color(0xFFEF9B1D))))
      }
      2 -> {
        path.moveTo(w*.12f,h*.75f);path.lineTo(0f,h*.25f);path.lineTo(w*.3f,h*.48f);path.lineTo(w*.5f,0f)
        path.lineTo(w*.7f,h*.48f);path.lineTo(w,h*.25f);path.lineTo(w*.88f,h*.75f);path.close()
        drawPath(path,Brush.linearGradient(listOf(Color(0xFFFFED8B),gold,Color(0xFFEB962B))))
        drawRoundRect(gold,Offset(w*.12f,h*.82f),Size(w*.76f,h*.12f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.05f))
      }
      else -> {
        path.moveTo(w*.5f,0f);path.lineTo(w*.65f,h*.35f);path.lineTo(w,h*.5f);path.lineTo(w*.65f,h*.65f)
        path.lineTo(w*.5f,h);path.lineTo(w*.35f,h*.65f);path.lineTo(0f,h*.5f);path.lineTo(w*.35f,h*.35f);path.close()
        drawPath(path,Brush.linearGradient(listOf(Color(0xFFEBD6FF),Color(0xFFAD70FF))))
      }
    }
  }
}

/** Decorative card backs carry no player data and remain outside semantics. */
@Composable
fun DexCardFan(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w=size.width;val h=size.height
    for(i in 0..2) {
      rotate(-22f+i*18f,Offset(w*.6f,h*.6f)) {
        val x=w*(.10f+i*.12f);val y=h*(.08f-i*.04f)
        val card=Size(w*.34f,h*1.02f)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF173D85),Color(0xFF10203E))),Offset(x,y),card,androidx.compose.ui.geometry.CornerRadius(w*.04f))
        drawRoundRect(if(i==2) Color(0xFF28C5ED) else Color(0xFF7661D5),Offset(x,y),card,androidx.compose.ui.geometry.CornerRadius(w*.04f),style=Stroke(w*.012f))
        val center=Offset(x+card.width/2,y+card.height*.52f)
        drawCircle(Color(0xFF79BDE7).copy(alpha=.14f),card.width*.35f,center)
        drawCircle(Color(0xFFAAC8EE).copy(alpha=.5f),card.width*.19f,center,style=Stroke(w*.015f))
        drawLine(Color(0xFFAAC8EE).copy(alpha=.5f),center-Offset(card.width*.19f,0f),center+Offset(card.width*.19f,0f),w*.015f)
        drawCircle(Color(0xFF10203E),card.width*.065f,center)
        drawCircle(Color(0xFFAAC8EE).copy(alpha=.8f),card.width*.04f,center)
      }
    }
  }
}
