package com.it10x.foodappgstav7_15.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.it10x.foodappgstav7_15.data.pos.entities.CategoryEntity

@Composable
fun CategorySidebar(
    categories: List<CategoryEntity>,
    selectedCatId: String?,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {

//    Surface(
//        modifier = modifier
//            .fillMaxHeight()
//            .widthIn(min = 90.dp, max = 130.dp),
//        tonalElevation = 0.dp,
//        color = MaterialTheme.colorScheme.surface,
//        shape = RoundedCornerShape(0.dp)
//    ) {


    //        LazyColumn(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(vertical = 6.dp),
//            verticalArrangement = Arrangement.spacedBy(6.dp),
//            contentPadding = PaddingValues(horizontal = 6.dp)
//        ) {



    Surface(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 90.dp, max = 130.dp),
        tonalElevation = 6.dp,
        color = androidx.compose.ui.graphics.Color.Transparent,
        //color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(0.dp)
    ){
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),

            verticalArrangement = Arrangement.spacedBy(6.dp),
          //  contentPadding = PaddingValues(6.dp)
                    contentPadding = PaddingValues(top=13.dp, start = 6.dp)

        ){

            items(categories) { category ->

                val isSelected = selectedCatId == category.id

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCategorySelected(category.id)
                        },
                    shape = RoundedCornerShape(10.dp),
                    color =
                        if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (isSelected) 4.dp else 0.dp
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight =
                                if (isSelected)
                                    FontWeight.SemiBold
                                else
                                    FontWeight.Normal,
                            color =
                                if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}