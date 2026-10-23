package aula;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import aula.obj.Md2Model;
import aula.obj.ObjModel;
import aula.obj.Vector2f;
import aula.util.TextureLoader;
import core3d.Ponto3D;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class Main {

	public static void main(String[] args) throws Exception {
		
		Path path = Paths.get(args.length > 0 ? args[0] : "../objs/x-35_obj.obj");
		if (!glfwInit())
			throw new IllegalStateException("GLFW nao iniciou");
		
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		long window = glfwCreateWindow(1024, 768, "Aula VBO - OBJ (sem shaders)", 0, 0);
		if (window == 0)
			throw new IllegalStateException("Falha ao criar janela");
		glfwMakeContextCurrent(window);
		glfwSwapInterval(1);
		GL.createCapabilities();
		glEnable(GL_DEPTH_TEST);
		glActiveTexture(GL_TEXTURE0);
		
		glClearColor(.5f, .5f, .5f, 1);
		try {
			ObjModel mesh = new ObjModel(path.toString());
			//ObjModel mesh2 = new ObjModel("src/main/resources/models/x-35_obj.obj");
			
			System.out.println("mesh "+mesh.drawboundingbox());
			//System.out.println("mesh "+mesh2.drawboundingbox());
					
			BufferedImage img = ImageIO.read(new File("src/main/resources/saida_textura_03___Default.png"));
			int textureId = TextureLoader.loadTexture(img);
			BufferedImage img_2 = ImageIO.read(new File("src/main/resources/saida_textura_glass.png"));
			int textureId2 = TextureLoader.loadTexture(img_2);
			
			mesh.setMaterialTexture("03___Default",textureId);
			mesh.setMaterialTexture("glass",textureId2);

			// ---------- MD2 (Quake 2) ----------
			Md2Model md2 = new Md2Model("../objs/boneco.md2");
			System.out.println("md2 "+md2.drawboundingbox());
			md2.loadPcx("../objs/boneco.pcx"); // a cor de cada pixel da skin fica em md2.textura[]

			// escala para o MD2 caber na tela (o Quake usa ~50 unidades de altura)
			float bboxMd2[] = md2.boundingbox();
			float escalaMd2 = 3.2f / Math.max(bboxMd2[3], Math.max(bboxMd2[4], bboxMd2[5]));

			boolean mostraMd2 = true; // M alterna entre o MD2 e o OBJ
			boolean mAntes = false, direitaAntes = false, esquerdaAntes = false, iAntes = false, espacoAntes = false;
			double ultimo = glfwGetTime();

			System.out.println("Vertices renderizados: " + mesh.f.size() + " | OBJ: " + path);
			double start = glfwGetTime();
			while (!glfwWindowShouldClose(window)) {

				if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
					glfwSetWindowShouldClose(window, true);

				// teclas do MD2 (agem uma vez por aperto)
				boolean teclaM = glfwGetKey(window, GLFW_KEY_M) == GLFW_PRESS;
				boolean direita = glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS;
				boolean esquerda = glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS;
				boolean teclaI = glfwGetKey(window, GLFW_KEY_I) == GLFW_PRESS;
				boolean espaco = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
				if (teclaM && !mAntes) {
					mostraMd2 = !mostraMd2;
				}
				if (direita && !direitaAntes) {
					md2.setAnimacao(md2.animAtual + 1);
				}
				if (esquerda && !esquerdaAntes) {
					md2.setAnimacao(md2.animAtual - 1);
				}
				if (teclaI && !iAntes) {
					md2.interpola = !md2.interpola;
					System.out.println("interpolacao " + (md2.interpola ? "ligada" : "desligada"));
				}
				if (espaco && !espacoAntes) {
					md2.pausa = !md2.pausa;
				}
				mAntes = teclaM;
				direitaAntes = direita;
				esquerdaAntes = esquerda;
				iAntes = teclaI;
				espacoAntes = espaco;

				// executa a animacao (interpola os keyframes)
				double agora = glfwGetTime();
				md2.atualizaAnimacao((float) (agora - ultimo));
				ultimo = agora;

				if (mostraMd2) {
					glfwSetWindowTitle(window, "MD2 - " + md2.animacoes.get(md2.animAtual).nome
							+ "  frames " + md2.frameA + " -> " + md2.frameB + "  t = " + ((int) (md2.t * 100)) / 100.0f
							+ (md2.interpola ? "" : "  (sem interpolacao)") + (md2.pausa ? "  PAUSA" : ""));
				} else {
					glfwSetWindowTitle(window, "Aula VBO - OBJ (sem shaders)");
				}
				int[] w = new int[1], h = new int[1];
				glfwGetFramebufferSize(window, w, h);
				if (h[0] == 0) {
					glfwPollEvents();
					continue;
				}
				
				
				glViewport(0, 0, w[0], h[0]);
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				glMatrixMode(GL_PROJECTION);
				glLoadIdentity();
				double aspect = (double) w[0] / h[0];
				//System.out.println("w0 "+w[0]+" h0 "+h[0]);
				glFrustum(-aspect * .7, aspect * .7, -.7, .7, 1, 100);
				glMatrixMode(GL_MODELVIEW);
				
				glLoadIdentity();
				glTranslatef(0, 0, -4);
				glRotatef((float) ((glfwGetTime() - start) * 25), 0, 1, 0);
				
				if(glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
					glRotatef(0, 1, 0, 0);
				}else {
					glRotatef(20, 1, 0, 0);
				}
				
				glPolygonMode(GL_FRONT_AND_BACK, glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS ? GL_LINE : GL_FILL);
				
				glColor3f(1, 1, 1);
				//System.out.println("textureId "+textureId);
				glEnable(GL_COLOR_MATERIAL);
				glEnable (GL_TEXTURE_2D);
				glBindTexture(GL_TEXTURE_2D, textureId);
				
				if (mostraMd2) {
					// MD2 desenhado por software no bufferDeVideo (Mat4x4, preencheTriangulo, Bresenham)
					limpaBuffer();
					float anguloY = (float) ((glfwGetTime() - start) * 25);
					float anguloX = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS ? 0 : 20;
					boolean wireframe = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
					md2.desenhaSe(escalaMd2, anguloY, anguloX, wireframe);

					// o OpenGL so mostra a imagem pronta
					mostraBufferDeVideo(w[0], h[0]);
				} else {
				glTranslatef(0, -1, 0);
				glScaled(0.05f,0.05f,0.05f);
				mesh.draw();
				}
				
				//glTranslatef(0, 0, -6);
				
				//glBindTexture(GL_TEXTURE_2D, textureId2);
				//glScaled(0.01f,0.01f,0.01f);
				
				//mesh2.draw();
				glfwSwapBuffers(window);
				glfwPollEvents();
			}
		} finally {
			glfwDestroyWindow(window);
			glfwTerminate();
		}
	}

	// =====================================================================
	// DESENHO POR SOFTWARE (mesmo esquema do MainCanvas dos projetos CG)
	// =====================================================================

	public static int W = 640;
	public static int H = 480;
	static int Largura = 640;
	static int Altura = 480;

	// 4 bytes por pixel, na ordem A, B, G, R (igual ao TYPE_4BYTE_ABGR do MainCanvas)
	static byte bufferDeVideo[] = new byte[W * H * 4];

	// profundidade do pixel ja desenhado: so desenha por cima se estiver mais perto
	static float zbuffer[] = new float[W * H];

	static ByteBuffer bufferTela = BufferUtils.createByteBuffer(W * H * 4);

	public static void limpaBuffer() {
		for (int i = 0; i < bufferDeVideo.length; i += 4) {
			bufferDeVideo[i] = (byte) 255;
			bufferDeVideo[i + 1] = (byte) 128;
			bufferDeVideo[i + 2] = (byte) 128;
			bufferDeVideo[i + 3] = (byte) 128;
		}
		for (int i = 0; i < zbuffer.length; i++) {
			zbuffer[i] = Float.MAX_VALUE;
		}
	}

	// ---- desenhaPixel do CG_3_1 (com o teste para nao sair do buffer) ----
	public static void desenhaPixel(int x, int y,int r,int g,int b) {

		if (x < 0 || x >= Largura || y < 0 || y >= Altura) {
			return;
		}

		int pospix = y*(W*4)+x*4;

		bufferDeVideo[pospix] = (byte)255;
		bufferDeVideo[pospix+1] = (byte)(b&0xff);
		bufferDeVideo[pospix+2] = (byte)(g&0xff);
		bufferDeVideo[pospix+3] = (byte)(r&0xff);

	}

	/**
	 * Algoritmo de Bresenham (implementado manualmente, so com inteiros,
	 * sem usar Graphics.drawLine ou qualquer funcao pronta de desenho de reta).
	 * Desenha uma linha entre (x1,y1) e (x2,y2) direto nos pixels do buffer.
	 * (copiado do CG_3_1)
	 */
	public static void desenhaLinha(int x1, int y1, int x2, int y2, int r, int g, int b) {

		int dx = Math.abs(x2 - x1);
		int dy = Math.abs(y2 - y1);

		int sx = (x1 < x2) ? 1 : -1;
		int sy = (y1 < y2) ? 1 : -1;

		int erro = dx - dy;

		int x = x1;
		int y = y1;

		while (true) {

			desenhaPixel(x, y, r, g, b);

			if (x == x2 && y == y2) {
				break;
			}

			int erro2 = 2 * erro;

			if (erro2 > -dy) {
				erro -= dy;
				x += sx;
			}
			if (erro2 < dx) {
				erro += dx;
				y += sy;
			}
		}
	}

	/*
	 * PREENCHE UM TRIANGULO pixel a pixel, com textura e z-buffer.
	 *
	 * a, b, c  -> vertices ja na tela (x, y em pixels; z = profundidade)
	 * ta,tb,tc -> coordenada de textura de cada vertice (0..1)
	 *
	 * Para cada pixel da caixa envolvente do triangulo:
	 *  1) calcula os pesos (coordenadas baricentricas) w0, w1, w2 com a "funcao de aresta":
	 *     de que lado da aresta o pixel esta. Se os 3 tiverem o mesmo sinal, esta dentro.
	 *  2) interpola a profundidade e compara com o zbuffer (o que esta atras nao aparece)
	 *  3) interpola a coordenada de textura (corrigida pela perspectiva: u/z, v/z e 1/z)
	 *  4) pega a cor da skin, aplica a luz e chama o desenhaPixel
	 */
	public static void preencheTriangulo(Ponto3D a, Ponto3D b, Ponto3D c, Vector2f ta, Vector2f tb, Vector2f tc,
			int textura[], int texW, int texH, float luz) {

		// triangulo atras da camera: nao desenha
		if (a.z <= 0.1f || b.z <= 0.1f || c.z <= 0.1f) {
			return;
		}

		float area = aresta(a, b, c.x, c.y);
		if (area == 0) {
			return;
		}

		// caixa envolvente, cortada nos limites da tela (para nao sair do buffer)
		int minX = (int) Math.max(0, Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
		int maxX = (int) Math.min(Largura - 1, Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
		int minY = (int) Math.max(0, Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
		int maxY = (int) Math.min(Altura - 1, Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f; // centro do pixel
				float py = y + 0.5f;

				float w0 = aresta(b, c, px, py) / area;
				float w1 = aresta(c, a, px, py) / area;
				float w2 = aresta(a, b, px, py) / area;
				if (w0 < 0 || w1 < 0 || w2 < 0) {
					continue; // fora do triangulo
				}

				// profundidade com correcao de perspectiva
				float umSobreZ = w0 / a.z + w1 / b.z + w2 / c.z;
				float prof = 1 / umSobreZ;

				int idx = y * Largura + x;
				if (prof >= zbuffer[idx]) {
					continue; // ja tem algo mais perto nesse pixel
				}
				zbuffer[idx] = prof;

				// textura com correcao de perspectiva
				float u = (w0 * ta.x / a.z + w1 * tb.x / b.z + w2 * tc.x / c.z) * prof;
				float v = (w0 * ta.y / a.z + w1 * tb.y / b.z + w2 * tc.y / c.z) * prof;
				int tx = (int) (u * texW);
				int ty = (int) (v * texH);
				if (tx < 0) tx = 0;
				if (ty < 0) ty = 0;
				if (tx >= texW) tx = texW - 1;
				if (ty >= texH) ty = texH - 1;

				int cor = textura[ty * texW + tx];
				int r = (int) (((cor >> 16) & 0xff) * luz);
				int g = (int) (((cor >> 8) & 0xff) * luz);
				int bl = (int) ((cor & 0xff) * luz);

				desenhaPixel(x, y, r, g, bl);
			}
		}
	}

	// "funcao de aresta": > 0 de um lado da reta p->q, < 0 do outro, 0 em cima dela
	static float aresta(Ponto3D p, Ponto3D q, float x, float y) {
		return (q.x - p.x) * (y - p.y) - (q.y - p.y) * (x - p.x);
	}

	/*
	 * Manda o bufferDeVideo para a janela. O OpenGL nao desenha nada do modelo:
	 * so copia a imagem pronta para a tela (glDrawPixels).
	 * O OpenGL comeca a imagem por baixo, por isso as linhas vao invertidas.
	 */
	public static void mostraBufferDeVideo(int larguraJanela, int alturaJanela) {
		bufferTela.clear();
		for (int y = H - 1; y >= 0; y--) {
			for (int x = 0; x < W; x++) {
				int p = y * W * 4 + x * 4;
				bufferTela.put(bufferDeVideo[p + 3]); // R
				bufferTela.put(bufferDeVideo[p + 2]); // G
				bufferTela.put(bufferDeVideo[p + 1]); // B
				bufferTela.put(bufferDeVideo[p]);     // A
			}
		}
		bufferTela.flip();

		glDisable(GL_DEPTH_TEST);
		glDisable(GL_TEXTURE_2D);
		glMatrixMode(GL_PROJECTION);
		glLoadIdentity();
		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();
		glRasterPos2f(-1, -1);
		glPixelZoom((float) larguraJanela / W, (float) alturaJanela / H); // estica 640x480 para o tamanho da janela
		glDrawPixels(W, H, GL_RGBA, GL_UNSIGNED_BYTE, bufferTela);
		glEnable(GL_DEPTH_TEST);
	}
}
