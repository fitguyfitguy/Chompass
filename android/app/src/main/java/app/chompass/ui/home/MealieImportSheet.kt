package app.chompass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.services.ai.AiError
import app.chompass.services.mealie.MealieClient
import app.chompass.services.mealie.MealieRecipeSummary
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.ChompassSheetLazyColumn
import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.theme.AppRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealieImportSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val state = rememberChompassSheetState()
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recipes by remember { mutableStateOf<List<MealieRecipeSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        host = container.prefs.mealieBaseUrl.first()
        token = container.keyStore.mealieToken().orEmpty()
    }

    val allowInsecure = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        allowInsecure.value = container.prefs.allowInsecureHttp.first()
    }

    fun persistCredentials() {
        scope.launch {
            container.prefs.setMealieBaseUrl(host)
            container.keyStore.setMealieToken(token)
        }
    }

    ChompassBottomSheet(
        onDismiss = onDismiss,
        sheetState = state,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.mealie_import_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.mealie_host_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppRadii.Field),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.mealie_token_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppRadii.Field),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy && host.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        persistCredentials()
                        busy = true
                        status = null
                        importedCount = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    MealieClient.listRecipes(host, token, allowInsecure.value)
                                }
                            }
                            busy = false
                            result.onSuccess { list ->
                                recipes = list
                                selected = list.map { it.slug }.toSet()
                                status = null
                            }.onFailure { err ->
                                recipes = emptyList()
                                selected = emptySet()
                                status = mealieErrorMessage(err)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.mealie_test_connection))
                }
                if (recipes.isNotEmpty()) {
                    TextButton(onClick = {
                        selected = if (selected.size == recipes.size) emptySet() else recipes.map { it.slug }.toSet()
                    }) {
                        Text(
                            if (selected.size == recipes.size) stringResource(R.string.mealie_select_none)
                            else stringResource(R.string.mealie_select_all),
                        )
                    }
                }
            }
            status?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            importedCount?.let { count ->
                Text(
                    stringResource(R.string.mealie_imported_format, count),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (recipes.isNotEmpty()) {
                val listState = rememberLazyListState()
                ChompassSheetLazyColumn(
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(recipes, key = { it.slug }) { item ->
                        val checked = item.slug in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - item.slug else selected + item.slug
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (it) selected + item.slug else selected - item.slug
                                },
                            )
                            Text(item.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SheetStickyPrimaryBar(
                    primaryLabel = if (busy) {
                        stringResource(R.string.mealie_importing)
                    } else {
                        stringResource(R.string.mealie_import_n_format, selected.size)
                    },
                    onPrimary = {
                        persistCredentials()
                        val slugs = selected.toList()
                        if (slugs.isEmpty()) return@SheetStickyPrimaryBar
                        busy = true
                        status = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val mapped = slugs.mapNotNull { slug ->
                                        MealieClient.getRecipe(host, token, slug, allowInsecure.value)
                                    }
                                    container.recipeRepository.upsertRecipes(mapped)
                                    mapped.size
                                }
                            }
                            busy = false
                            result.onSuccess { count ->
                                importedCount = count
                            }.onFailure { err ->
                                status = mealieErrorMessage(err)
                            }
                        }
                    },
                    primaryEnabled = !busy && selected.isNotEmpty(),
                )
            }
        }
    }
}

private fun mealieErrorMessage(err: Throwable): String = when (err) {
    is AiError.InsecureHttpBlocked -> "HTTP is blocked. Turn on Allow insecure HTTP in AI settings, or use HTTPS."
    else -> err.message?.takeIf { it.isNotBlank() } ?: err::class.simpleName.orEmpty()
}
