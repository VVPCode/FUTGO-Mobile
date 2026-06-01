package com.example.futgo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

// ==========================================
// 1. MODELO DE DADOS COMPLETO
// ==========================================
@Suppress("SpellCheckingInspection")
data class Produto(
    val id: String? = null,
    @SerializedName("nome_camisa") val nomeCamisa: String = "",
    val preco: Double = 0.0,
    val categoria: String = "Nacional",
    val imagem: String = "",
    val imagens: List<String> = emptyList(),
    val cores: List<String> = emptyList(),
    val pais: String = "",
    val liga: String = "",
    val tamanhos: List<String> = listOf("P", "M", "G", "GG"),
    val temporada: String = "",
    @SerializedName("tipo_uniforme") val tipoUniforme: String = "Primeira Camisa",
    val marca: String = "",
    val genero: String = "Unissex",
    val personalizavel: Boolean = false
)

data class UploadResponse(val urls: List<String>)

// --- NOVOS MODELOS DE PEDIDO ---
data class ItemPedido(
    val id: String = "",
    @SerializedName("nome_camisa") val nomeCamisa: String = "",
    val tamanho: String = "",
    val quantidade: Int = 0,
    val preco: Double = 0.0
)

data class Pedido(
    @SerializedName("id_pedido") val idPedido: String = "",
    @SerializedName("id_usuario") val idUsuario: String = "",
    @SerializedName("data_pedido") val dataPedido: String = "",
    val total: Double = 0.0,
    val status: String = "",
    val itens: List<ItemPedido> = emptyList()
)

data class StatusUpdateRequest(val status: String)

// ==========================================
// 2. CONFIGURAÇÕES GLOBAIS
// ==========================================
const val ADMIN_USER_ID = "1773108903"

interface FutgoApi {
    // Produtos
    @GET("produtos/")
    suspend fun getProdutos(): List<Produto>

    @POST("produtos/")
    suspend fun createProduto(@Header("X-User-ID") adminId: String, @Body produto: Produto): Produto

    @PUT("produtos/{id}/")
    suspend fun updateProduto(@Header("X-User-ID") adminId: String, @Path("id") id: String, @Body produto: Produto): Produto

    @DELETE("produtos/{id}/")
    suspend fun deleteProduto(@Header("X-User-ID") adminId: String, @Path("id") id: String)

    @Multipart
    @POST("upload-imagens/")
    suspend fun uploadImagem(@Header("X-User-ID") adminId: String, @Part imagens: MultipartBody.Part): UploadResponse

    // Pedidos
    @GET("pedidos/admin/")
    suspend fun getPedidosAdmin(@Header("X-User-ID") adminId: String): List<Pedido>

    @PUT("pedidos/{id_pedido}/status/")
    suspend fun updatePedidoStatus(
        @Header("X-User-ID") adminId: String,
        @Path("id_pedido") idPedido: String,
        @Body request: StatusUpdateRequest
    )
}

// Gestor Dinâmico de Ligação
object RetrofitClient {
    var api: FutgoApi? = null

    fun conectar(ip: String) {
        val baseUrl = "http://$ip:8000/api/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(FutgoApi::class.java)
    }
}

// ==========================================
// 3. FUNÇÃO AUXILIAR: URI PARA FICHEIRO E DATAS
// ==========================================
fun uriToFile(context: Context, uri: Uri): File? {
    val contentResolver = context.contentResolver
    val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
    return try {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val outputStream = FileOutputStream(tempFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun formatarData(dataISO: String): String {
    return try {
        // Tentativa de formatar a data que vem do Django (ISO 8601)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val formatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        val date = parser.parse(dataISO.take(19)) // Ignora milissegundos e Z para simplicidade
        if (date != null) formatter.format(date) else dataISO
    } catch (e: Exception) {
        dataISO
    }
}

// ==========================================
// 4. VIEWMODEL
// ==========================================
@Suppress("SpellCheckingInspection")
class ProdutoViewModel : ViewModel() {
    private val _produtos = MutableStateFlow<List<Produto>>(emptyList())
    val produtos: StateFlow<List<Produto>> = _produtos

    private val _pedidos = MutableStateFlow<List<Pedido>>(emptyList())
    val pedidos: StateFlow<List<Pedido>> = _pedidos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun conectarServidor(ip: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                RetrofitClient.conectar(ip)
                _produtos.value = RetrofitClient.api!!.getProdutos()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Falha na ligação. Verifique o IP.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchProdutos() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _produtos.value = RetrofitClient.api!!.getProdutos()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchPedidos(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _pedidos.value = RetrofitClient.api!!.getPedidosAdmin(ADMIN_USER_ID)
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Erro ao carregar pedidos.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun atualizarStatusPedido(idPedido: String, novoStatus: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                RetrofitClient.api!!.updatePedidoStatus(ADMIN_USER_ID, idPedido, StatusUpdateRequest(novoStatus))
                fetchPedidos(onError) // Atualiza a lista após sucesso
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Erro ao atualizar status.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun salvarProdutoComImagem(context: Context, imageUri: Uri?, produto: Produto, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var finalImageUrl = produto.imagem
                var finalImagesList = produto.imagens

                if (imageUri != null) {
                    val file = uriToFile(context, imageUri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("imagens", file.name, requestFile)

                        val response = RetrofitClient.api!!.uploadImagem(ADMIN_USER_ID, body)
                        if (response.urls.isNotEmpty()) {
                            finalImageUrl = response.urls[0]
                            finalImagesList = listOf(finalImageUrl)
                        }
                    }
                }

                val produtoSalvar = produto.copy(imagem = finalImageUrl, imagens = finalImagesList)
                if (produtoSalvar.id.isNullOrEmpty()) {
                    RetrofitClient.api!!.createProduto(ADMIN_USER_ID, produtoSalvar)
                } else {
                    RetrofitClient.api!!.updateProduto(ADMIN_USER_ID, produtoSalvar.id, produtoSalvar)
                }

                fetchProdutos()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Erro ao salvar: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletarProduto(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                RetrofitClient.api!!.deleteProduto(ADMIN_USER_ID, id)
                fetchProdutos()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ==========================================
// 5. INTERFACE GRÁFICA (UI COMPOSE)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ProdutoViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf("IP") }
    var selectedProduto by remember { mutableStateOf<Produto?>(null) }

    when (currentScreen) {
        "IP" -> IpConnectScreen(
            viewModel = viewModel,
            onConnected = { currentScreen = "LIST" }
        )
        "LIST" -> ProdutoListScreen(
            viewModel = viewModel,
            onAddClick = {
                selectedProduto = null
                currentScreen = "FORM"
            },
            onProdutoClick = { produto ->
                selectedProduto = produto
                currentScreen = "FORM"
            },
            onVerPedidosClick = {
                currentScreen = "PEDIDOS"
            }
        )
        "FORM" -> ProdutoFormScreen(
            viewModel = viewModel,
            produtoInicial = selectedProduto,
            onBack = { currentScreen = "LIST" }
        )
        "PEDIDOS" -> PedidoListScreen(
            viewModel = viewModel,
            onBack = { currentScreen = "LIST" }
        )
    }
}

// TELA 1: CONEXÃO IP
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpConnectScreen(viewModel: ProdutoViewModel, onConnected: () -> Unit) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()

    var ipServidor by remember { mutableStateOf("192.168.") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FUTGO Admin", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = "Conexão", modifier = Modifier.size(80.dp), tint = Color(0xFF16A34A))
            Spacer(modifier = Modifier.height(24.dp))

            Text("Ligar ao Servidor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Insira o IP local do seu computador.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

            OutlinedTextField(
                value = ipServidor,
                onValueChange = { ipServidor = it },
                label = { Text("Ex: 192.168.0.6") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (ipServidor.isBlank()) {
                        Toast.makeText(context, "Por favor, insira o IP.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.conectarServidor(
                        ip = ipServidor.trim(),
                        onSuccess = {
                            Toast.makeText(context, "Conectado!", Toast.LENGTH_SHORT).show()
                            onConnected()
                        },
                        onError = { erro ->
                            Toast.makeText(context, erro, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Conectar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// TELA 2: LISTA DE PRODUTOS
@Suppress("SpellCheckingInspection")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoListScreen(
    viewModel: ProdutoViewModel,
    onAddClick: () -> Unit,
    onProdutoClick: (Produto) -> Unit,
    onVerPedidosClick: () -> Unit
) {
    val produtos by viewModel.produtos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo FUTGO", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onVerPedidosClick) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = "Ver Pedidos", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color(0xFF16A34A)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar Produto", tint = Color.White)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && produtos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(produtos) { produto ->
                        ProdutoCard(produto, onClick = { onProdutoClick(produto) })
                    }
                }
            }
        }
    }
}

@Suppress("SpellCheckingInspection")
@Composable
fun ProdutoCard(produto: Produto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = produto.imagem.ifEmpty { "https://placehold.co/100?text=Sem+Foto" },
                contentDescription = null,
                modifier = Modifier.size(70.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = produto.nomeCamisa, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(text = "Categoria: ${produto.categoria} | Marca: ${produto.marca}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "R$ ${String.format(Locale.US, "%.2f", produto.preco)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

// TELA 3: GESTÃO DE PEDIDOS (NOVA)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoListScreen(viewModel: ProdutoViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val pedidos by viewModel.pedidos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Carrega os pedidos ao abrir a tela
    LaunchedEffect(Unit) {
        viewModel.fetchPedidos { erro -> Toast.makeText(context, erro, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestão de Pedidos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF8FAFC))) {
            if (isLoading && pedidos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pedidos.isEmpty()) {
                Text("Nenhum pedido recebido ainda.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(pedidos) { pedido ->
                        PedidoCard(pedido = pedido, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PedidoCard(pedido: Pedido, viewModel: ProdutoViewModel) {
    val context = LocalContext.current
    var isStatusMenuExpanded by remember { mutableStateOf(false) }
    val statuses = listOf("Aguardando Pagamento", "Recebido", "Em Separação", "Enviado", "Entregue", "Cancelado")

    val statusColor = when(pedido.status) {
        "Entregue" -> Color(0xFF16A34A) // Verde
        "Cancelado" -> Color(0xFFDC2626) // Vermelho
        "Recebido" -> Color(0xFF2563EB) // Azul
        else -> Color(0xFFD97706) // Laranja/Amarelo
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // CABEÇALHO DO PEDIDO
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(text = "ID: ${pedido.idPedido.take(8).uppercase()}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Text(text = formatarData(pedido.dataPedido), fontSize = 12.sp, color = Color.Gray)
                }
                Text(text = "R$ ${String.format(Locale.US, "%.2f", pedido.total)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF16A34A))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // LISTA DE ITENS
            Text("Itens do Pedido:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
            pedido.itens.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${item.quantidade}x ${item.nomeCamisa} (${item.tamanho})", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                    Text(text = "R$ ${String.format(Locale.US, "%.2f", item.preco)}", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ATUALIZAÇÃO DE STATUS (DROPDOWN)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Cliente ID: ${pedido.idUsuario}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))

                Box {
                    Button(
                        onClick = { isStatusMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = pedido.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Alterar", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = isStatusMenuExpanded,
                        onDismissRequest = { isStatusMenuExpanded = false }
                    ) {
                        statuses.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s, fontSize = 14.sp) },
                                onClick = {
                                    isStatusMenuExpanded = false
                                    if (s != pedido.status) {
                                        viewModel.atualizarStatusPedido(
                                            idPedido = pedido.idPedido,
                                            novoStatus = s,
                                            onSuccess = { Toast.makeText(context, "Status atualizado!", Toast.LENGTH_SHORT).show() },
                                            onError = { erro -> Toast.makeText(context, erro, Toast.LENGTH_SHORT).show() }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// TELA 4: FORMULÁRIO DE PRODUTO
@Suppress("SpellCheckingInspection")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoFormScreen(
    viewModel: ProdutoViewModel,
    produtoInicial: Produto?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()

    // Variáveis de Estado
    var nome by remember { mutableStateOf(produtoInicial?.nomeCamisa ?: "") }
    var preco by remember { mutableStateOf(produtoInicial?.preco?.toString() ?: "") }
    var categoria by remember { mutableStateOf(produtoInicial?.categoria ?: "Nacional") }
    var marca by remember { mutableStateOf(produtoInicial?.marca ?: "") }
    var pais by remember { mutableStateOf(produtoInicial?.pais ?: "") }
    var liga by remember { mutableStateOf(produtoInicial?.liga ?: "") }
    var temporada by remember { mutableStateOf(produtoInicial?.temporada ?: "") }
    var tipoUniforme by remember { mutableStateOf(produtoInicial?.tipoUniforme ?: "Primeira Camisa") }
    var genero by remember { mutableStateOf(produtoInicial?.genero ?: "Unissex") }

    var cores by remember { mutableStateOf(produtoInicial?.cores?.joinToString(", ") ?: "") }
    var tamanhos by remember { mutableStateOf(produtoInicial?.tamanhos?.joinToString(", ") ?: "P, M, G, GG") }
    var personalizavel by remember { mutableStateOf(produtoInicial?.personalizavel ?: false) }
    var urlImagemOriginal by remember { mutableStateOf(produtoInicial?.imagem ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val isEditing = produtoInicial != null

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) { selectedImageUri = uri } }
    )

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Produto" else "Novo Produto") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            produtoInicial?.id?.let { idProduto ->
                                viewModel.deletarProduto(idProduto) {
                                    Toast.makeText(context, "Produto Apagado!", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = Color.Red)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Fotografia Principal", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    val modelImage = selectedImageUri ?: urlImagemOriginal.ifEmpty { null }

                    if (modelImage != null) {
                        AsyncImage(model = modelImage, contentDescription = "Pré-visualização", modifier = Modifier.size(120.dp))
                    } else {
                        Box(modifier = Modifier.size(120.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(50.dp), tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text(if (modelImage != null) "Trocar Fotografia" else "Selecionar da Galeria")
                    }
                }
            }

            Text("Informações Principais", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome da Camisa *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = preco, onValueChange = { preco = it }, label = { Text("Preço (R$) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = categoria, onValueChange = { categoria = it }, label = { Text("Categoria (Ex: Nacional, Seleções)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca / Fornecedor (Ex: Nike, Adidas)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Detalhes", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(value = pais, onValueChange = { pais = it }, label = { Text("País") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = liga, onValueChange = { liga = it }, label = { Text("Liga / Campeonato") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = temporada, onValueChange = { temporada = it }, label = { Text("Temporada (Ex: 23/24)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = tipoUniforme, onValueChange = { tipoUniforme = it }, label = { Text("Tipo (Ex: Primeira Camisa, Retrô)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = genero, onValueChange = { genero = it }, label = { Text("Género (Ex: Unissex, Masculino)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Opções de Compra", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                value = tamanhos,
                onValueChange = { tamanhos = it },
                label = { Text("Tamanhos Disponíveis") },
                placeholder = { Text("Ex: P, M, G, GG") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cores,
                onValueChange = { cores = it },
                label = { Text("Cores Disponíveis") },
                placeholder = { Text("Ex: Azul, Branco") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Permite Personalização?", color = Color.DarkGray)
                Switch(
                    checked = personalizavel,
                    onCheckedChange = { personalizavel = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF16A34A), checkedTrackColor = Color(0xFFBBF7D0))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val precoNum = preco.toDoubleOrNull() ?: 0.0
                    if (nome.isBlank() || precoNum <= 0.0) {
                        Toast.makeText(context, "Preencha o nome e um preço válido.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val listaCores = cores.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val listaTamanhos = tamanhos.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    val produtoSalvar = Produto(
                        id = produtoInicial?.id,
                        nomeCamisa = nome,
                        preco = precoNum,
                        categoria = categoria,
                        imagem = urlImagemOriginal,
                        imagens = if (urlImagemOriginal.isNotEmpty()) listOf(urlImagemOriginal) else emptyList(),
                        cores = listaCores,
                        pais = pais,
                        liga = liga,
                        tamanhos = listaTamanhos,
                        temporada = temporada,
                        tipoUniforme = tipoUniforme,
                        marca = marca,
                        genero = genero,
                        personalizavel = personalizavel
                    )

                    viewModel.salvarProdutoComImagem(
                        context = context,
                        imageUri = selectedImageUri,
                        produto = produtoSalvar,
                        onSuccess = {
                            Toast.makeText(context, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        onError = { erro ->
                            Toast.makeText(context, erro, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Guardar Produto", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}