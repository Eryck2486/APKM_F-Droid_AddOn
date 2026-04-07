package com.apkm.addon.fdroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class ServiceWorker extends Service {
    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    String APKM_SOCKET_FEEDBACK = "apkm_socket_feedback";
    private static final String CHANNEL_ID = "CanalServicoThread";
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());


    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Canal de Exemplo",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        criarCanalNotificacao();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Serviço Ativo")
                .setContentText("A thread está executando em segundo plano...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();

        //Estabelece o nome do socket
        String SOCKET_NAME = UUID.randomUUID().toString();
        //Parser para o Gson
        Gson gson = new Gson();
        //Inicia o Looper para rodar a thread de escuta para o APKM (não travar o Looper principal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
        new Thread(() -> {
            try {
                LocalServerSocket server = new LocalServerSocket(SOCKET_NAME);
                Provider.Configs.SOCKET_NAME = SOCKET_NAME;
                // Thread para não travar o Looper principal

                    while (true) {
                        try (LocalSocket client = server.accept();
                             java.io.BufferedReader in = new java.io.BufferedReader(
                                     new java.io.InputStreamReader(client.getInputStream()));
                             java.io.PrintWriter out = new java.io.PrintWriter(client.getOutputStream(), true)) {
                            // Lê a entrada do C++ (bloqueia até receber uma linha)
                            String comando = in.readLine();
                            if (comando != null && !comando.isEmpty()) {
                                Log.i("Comando recebido", comando);
                                {
                                    // Lógica de roteamento baseada na entrada
                                    //Em algumas funções o APKM abre o socket para monitorar o estado da atividade, util para que o usuário não pense que a aplicação travou durante os downloads
                                    if (comando.startsWith("search")) {
                                        //Procura por pacotes no servidor e retorna um cursor com os pacotes encontrados
                                        String termo = comando.substring(7);
                                        String urlSearch = "https://search.f-droid.org/?q=" + termo + "&lang=" + Locale.getDefault().getLanguage();
                                        List<Provider.PacoteAddon> Busca;
                                        try {
                                            Busca = searchPackages(urlSearch);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                        for (Provider.PacoteAddon result : Busca) {
                                            String jsonrespostaTmp = gson.toJson(result);
                                            out.println(jsonrespostaTmp);
                                        }
                                    } else if (comando.contains("getPackage")) {
                                        String pacote = comando.substring(11);
                                        String jsonretorno = dwAndPrepareInstall(pacote);
                                        out.println(jsonretorno);
                                        sendMessageToApkmSocket("\"END\":{\"status\":\"success\",\"message\":\"Consulta realizada com sucesso\"}");
                                        Log.i("Pacote baixado", pacote);
                                    } else if (comando.contains("getUpdate")) {
                                        String jsonretorno = updatePrepareInstall(comando.substring(11));
                                        out.println(jsonretorno);
                                    }else if(comando.contains("cleanAll")){
                                        cleanAll();
                                    }
                                    sendMessageToApkmSocket("\"END\":{\"status\":\"success\",\"message\":\"Consulta realizada com sucesso\"}");
                                }
                            }
                            out.flush();
                        } catch (Exception e) {
                            // Log de erro interno para o logcat
                            sendMessageToApkmSocket("\"END\":{\"status\":\"fail\",\"message\":\"" + e.getMessage() + "\"}");
                            android.util.Log.e("APKM_SERVICE", "Erro na conexão: " + e.getMessage());
                        }
                    }
            }catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            mainThreadHandler.post(() -> {
                stopForeground(true);
                stopSelf();
            });
        }).start();
        return START_STICKY;
    }


    void cleanAll(){
        getFilesDir().delete();
        //Reiniciando serviço para interromper tarefas que falharam
        Intent intent = new Intent();
        intent.setClassName(this, ServiceWorker.class.getName());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e("Provider", "Failed to start service during cleanAll: " + e.getMessage());
        }
    }

    void sendMessageToApkmSocket(String message){
        /*dados enviados nesse socket no formato JSON correto serão exibidos no terminal
        como o feedback não é uma função crítica a excessão é descartada por padrão

        JSON de progresso do download:
        "DOWNLOADING" : {
            "percent" : "10",
            "message" : "Downloading APK"
            "size" : "100MB"
        }

        JSON de erro:
        "END" : {
            "status" : "fail",
            "message" : "Erro ao baixar APK"
        }

        JSON de sucesso:
        "END" : {
            "status" : "success",
            "message" : "APK baixado com sucesso"
        }

        JSON de status de checagem PGP
        "END" : {
            "status" : "checking",
            "message" : "Verificando assinatura do APK"
         }

         JSON de inicialização do download:
         "DOWNLOADING":{
            "percent":"0",
            "message":"Iniciando download",
            "size":"100MB"
         }
        */
        new Thread(() -> {
            try {
                LocalSocket socket = new LocalSocket();
                socket.connect(new LocalSocketAddress(APKM_SOCKET_FEEDBACK));
                socket.getOutputStream().write(message.getBytes());
                socket.close();
            } catch (IOException ignored) {
            }
        }).start();
    }

    String updatePrepareInstall(String updateServerUrl){
        //Lógica para fornecer o apk da nova versão do AddOn
        return "{\"status\":\"fail\",\"packageFile\":\"\",\"package\":\"\"}";
    }
    List<Provider.PacoteAddon> searchPackages(String url) throws IOException {
        //Fazendo a busca na pagina de buscas do fdroid https://search.f-droid.org/?q=<termo aqui>&lang=<idioma aqui>
        List<Provider.SearchResult> resultsTmp = new ArrayList<>();
        {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .get();
            Elements pacotes = doc.select("a.package-header");
            for (Element pacote : pacotes) {
                Provider.SearchResult result = new Provider.SearchResult();
                result.name = pacote.select("h4.package-name").text();
                result.description = pacote.select("span.package-summary").text();
                result.packageLicense = pacote.select("span.package-license").text();
                result.packageID = pacote.attr("href").split("/")[5];
                resultsTmp.add(result);
            }
        }

        //Verificando dados adicionais em subpagina https://f-droid.org/packages/<pacoteID> com busca paralelizada
        ExecutorService executor = Executors.newFixedThreadPool(5); // 5 requisições por vez
        List<Future<Provider.PacoteAddon>> futures = new ArrayList<>();
        for(Provider.SearchResult result : resultsTmp) {
            futures.add( executor.submit(() -> {
                Provider.PacoteAddon pacote = new Provider.PacoteAddon();
                pacote.pacote = result.packageID;
                pacote.nome = result.name;
                pacote.descricao = result.description;
                return getPacoteDetails(pacote, true);
            }));
            //Adicionando prefixo para seleção direta no apkm
        }

        List<Provider.PacoteAddon> results = new ArrayList<>();
        for (Future<Provider.PacoteAddon> future : futures) {
            try {
                Provider.PacoteAddon result = future.get();
                result.pacote= getString(R.string.prefix) + ":" +result.pacote;
                results.add(result);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    Provider.PacoteAddon getPacoteDetails(Provider.PacoteAddon pacote, boolean basicInfos){
        String urlPagina = "https://f-droid.org/packages/"+pacote.pacote;
        try {
            Document doc = Jsoup.connect(urlPagina)
                    .userAgent(userAgent)
                    .get();
            //Obtendo a versão mais recente ("Recomended")
            Element versaoRecente = doc.select("li#latest").first();
            Element icone = doc.select("img.package-icon").first();
            //Transformar arquivo PNG de src em Base64
            assert icone != null;
            String iconeUrl = icone.attr("src");
            if(iconeUrl.startsWith("/assets")){
                iconeUrl = "https://f-droid.org" + iconeUrl;
            }
            pacote.icon = iconeUrl;
            if (versaoRecente != null) {
                // 2. Link do APK (Fica dentro de um parágrafo class="package-version-download")
                //Obtendo lista de arquiteturas do pacote
                String arquiteturaBruta = versaoRecente.select("p.package-version-nativecode").text();
                pacote.arquiteturas = formatarArquiteturas(arquiteturaBruta); // "all" ou as arquiteturas específicas
                pacote.versao = versaoRecente.select("div.package-version-header a").eachAttr("name").toArray()[0].toString();
                if (!basicInfos) {
                    //Obtendo urls [app.apk, app.apk.asc, source.log.gz]
                    List<String> pacUrls = versaoRecente.select("p.package-version-download a").eachAttr("href");
                    //app.apk é o arquivo APK
                    //app.apk.asc é a chave PGP
                    //source.log.gz é o log de compilação do pacote
                    pacote.endereco = pacUrls.get(0);
                    pacote.sha256sumOrPGPLink = pacUrls.get(1);
                }
            }
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pacote;
    }

    private List<String> formatarArquiteturas(String texto) {
        List<String> arquiteturas = new ArrayList<>();
        if (texto.isEmpty()){
            arquiteturas.add("all");
            return arquiteturas;
        }
        String[] archs = texto.split(" ");
        for (String arch : archs) {
            arch = arch.trim();
            if (!arch.isEmpty() && !arch.equals("nativa:")) {
                arquiteturas.add(arch);
            }
        }
        return arquiteturas;
    }

    //Baixa e verifica o apk antes de retornar para o apkm
    String dwAndPrepareInstall(String pacote){
        String packageLocation;
        Provider.PacoteAddon pacoteAddon = new Provider.PacoteAddon();
        pacoteAddon.pacote = pacote;
        pacoteAddon=getPacoteDetails(pacoteAddon, false);
        if (pacoteAddon.endereco != null) {
            sendMessageToApkmSocket("\"DOWNLOADING\":{\"percent\":\"0\",\"message\":\"Iniciando download\",\"size\":\""+bytesToHSize(pacoteAddon.endereco.length())+"\"}");
            //baixando apk e pgp e verificando pgp
            Log.i("Link do APK", pacoteAddon.endereco);
            Log.i("Link do PGP", pacoteAddon.sha256sumOrPGPLink);
            File packageFile = fileDownloader( pacoteAddon.endereco, pacoteAddon.pacote+".apk");
            File pgpFile = fileDownloader(pacoteAddon.sha256sumOrPGPLink, pacoteAddon.pacote+".apk.asc");

            Log.i("Arquivo APK", packageFile.getAbsolutePath());
            Log.i("Arquivo PGP", pgpFile.getAbsolutePath());
            if(verificarAPK(packageFile, pgpFile)){
                sendMessageToApkmSocket("\"END\":{\"status\":\"checking\",\"message\":\"Verificando assinatura do APK\"}");
                return "{\"status\":\"success\",\"packageFile\":\""+packageFile+"\",\"package\":\""+pacoteAddon.pacote+"\"}";
            }
            //libera o acesso ao arquivo APK com PosixPermission
            if (packageFile != null) {
                packageFile.setReadable(true, false);
                packageFile.setExecutable(true, false);
                packageFile.setWritable(true, false);
            }
        }
        return "{\"status\":\"fail\",\"packageFile\":\"\",\"package\":\"\"}";
    }

    public File fileDownloader(String urltmp, String fileName) {
        try {
            URL url = new URL(urltmp);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            File file = new File(getExternalFilesDir("Downloads"), fileName);
            if (file.exists()) file.delete();

            InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[8192]; // Buffer um pouco maior para eficiência
            int bytesRead;
            long fileLength = connection.getContentLength();
            String size = bytesToHSize(fileLength);

            int ultimaPorcentagem = -1;
            long ultimoEnvio = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);

                // Lógica para não inundar o socket
                int porcentagem = (int) ((file.length() * 100) / fileLength);
                long agora = System.currentTimeMillis();

                // Só envia se a porcentagem mudou OU se passou 500ms (para arquivos grandes)
                if (porcentagem != ultimaPorcentagem && (agora - ultimoEnvio > 100)) {
                    ultimaPorcentagem = porcentagem;
                    ultimoEnvio = agora;

                    String mensagem = "\"DOWNLOADING\":{\"percent\":\"" + porcentagem +
                            "\",\"message\":\"Baixando " + fileName + "\"" +
                            ",\"size\":\"" + size + "\"}";
                    sendMessageToApkmSocket(mensagem);
                }
            }
            outputStream.close();
            inputStream.close();
            return file;
        } catch (Exception e) {
            sendMessageToApkmSocket("{\"END\":{\"status\":\"fail\",\"message\":\"" + e.getMessage() + "\"}}");
            return null;
        }
    }

    //Converte bytes para um formato legível (como KB, MB, GB)
    String bytesToHSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public boolean verificarAPK(File arquivoApk, File arquivoAssinatura){
        try {
            InputStream chavePublicaIn = getAssets().open("admin@f-droid.org.asc");
            // 1. Carrega a chave pública do F-Droid
            PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(chavePublicaIn), new JcaKeyFingerprintCalculator());

            // 2. Abre o arquivo de assinatura (.asc)
            InputStream sigIn = PGPUtil.getDecoderStream(new FileInputStream(arquivoAssinatura));
            PGPObjectFactory pgpFact = new PGPObjectFactory(sigIn, new JcaKeyFingerprintCalculator());

            // 3. Extrai a lista de assinaturas
            Object obj = pgpFact.nextObject();
            PGPSignatureList sigList = (obj instanceof PGPSignatureList) ? (PGPSignatureList) obj : (PGPSignatureList) pgpFact.nextObject();
            PGPSignature assinatura = sigList.get(0);

            // 4. Busca a chave correspondente no seu "chaveiro" (KeyRing)
            PGPPublicKey chave = pgpPub.getPublicKey(assinatura.getKeyID());
            if (chave == null) return false; // Assinado por uma chave que você não confia

            // 5. Inicializa a verificação
            assinatura.init(
                    new JcaPGPContentVerifierBuilderProvider()
                            .setProvider(new BouncyCastleProvider()), // Passa o objeto em vez da String "BC"
                    chave
            );

            // 6. Lê o APK e atualiza o verificador
            FileInputStream apkIn = new FileInputStream(arquivoApk);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = apkIn.read(buffer)) > 0) {
                assinatura.update(buffer, 0, len);
            }
            apkIn.close();

            // 7. O veredito final
            return assinatura.verify();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
