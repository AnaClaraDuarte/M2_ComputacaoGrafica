package aula.obj;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import aula.Main;
import core3d.Mat4x4;
import core3d.Ponto3D;

/*
 * Modelo MD2 (Quake 2) - animacao por keyframes.
 * Desenhado por SOFTWARE, como nos projetos CG: transformacoes com a Mat4x4/Ponto3D,
 * triangulos preenchidos pixel a pixel (Main.preencheTriangulo) e arestas com o
 * Bresenham (Main.desenhaLinha) dentro do Main.bufferDeVideo.
 *
 * Arquivo MD2 (inteiros e floats de 4 bytes, shorts de 2 bytes, little-endian):
 *
 *  HEADER (68 bytes)
 *    ident "IDP2", version 8, skinwidth, skinheight, framesize,
 *    num_skins, num_vertices, num_st, num_tris, num_glcmds, num_frames,
 *    ofs_skins, ofs_st, ofs_tris, ofs_frames, ofs_glcmds, ofs_end
 *
 *  ST     -> num_st   x { short s, short t }          (UV em pixels)
 *  TRIS   -> num_tris x { short v[3], short st[3] }   (indices do vertice e da UV)
 *  FRAMES -> num_frames x { float scale[3], float translate[3], char nome[16],
 *                           num_vertices x { byte x, y, z, normalIndex } }
 *
 *  posicao real do vertice = byte * scale + translate
 */
public class Md2Model {

	public ArrayList<Vector2f> vt;                   // coordenadas de textura (0..1)
	public ArrayList<Face3D> f;                      // triangulos (indices comecam em 0)
	public ArrayList<ArrayList<Vector3f>> frames;    // keyframes: lista de vertices de cada frame
	public ArrayList<String> nomesFrames;            // "stand01", "run03", ...
	public ArrayList<GrupoFaces> animacoes;          // nome + frame inicial/final de cada animacao
	public ArrayList<Vector3f> posicoes;             // pose atual (interpolada entre frameA e frameB)

	public int skinwidth;
	public int skinheight;
	public int numVertices;
	public int numTris;
	public int numFrames;

	public int textura[] = null;  // cores da skin (0xRRGGBB), preenchida pelo loadPcx
	public int texW = 0;
	public int texH = 0;

	public Vector3f bbox_min = null;
	public Vector3f bbox_max = null;

	// ---- controle da animacao ----
	public int animAtual = 0;
	public float tempo = 0;          // em "frames" desde o inicio da animacao
	public float fps = 8;            // keyframes por segundo
	public boolean interpola = true; // false = pula de keyframe em keyframe
	public boolean pausa = false;
	public int frameA = 0;
	public int frameB = 0;
	public float t = 0;              // quanto andar de A ate B (0..1)

	public Md2Model(String file) {
		vt = new ArrayList<Vector2f>();
		f = new ArrayList<Face3D>();
		frames = new ArrayList<ArrayList<Vector3f>>();
		nomesFrames = new ArrayList<String>();
		animacoes = new ArrayList<GrupoFaces>();
		posicoes = new ArrayList<Vector3f>();

		loadMd2(file);
		decodeAnimacoes();

		for (int i = 0; i < numVertices; i++) {
			posicoes.add(new Vector3f());
		}
		interpolaFrames();
	}

	// ================== LEITURA DO ARQUIVO ==================

	public void loadMd2(String file) {
		byte dados[] = null;
		try {
			File arq = new File(file);
			FileInputStream fin = new FileInputStream(arq);
			dados = new byte[(int) arq.length()];
			int lidos = 0;
			while (lidos < dados.length) {
				int n = fin.read(dados, lidos, dados.length - lidos);
				if (n < 0) {
					break;
				}
				lidos += n;
			}
			fin.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// "IDP2" = 'I','D','P','2'
		if (dados[0] != 'I' || dados[1] != 'D' || dados[2] != 'P' || dados[3] != '2' || leInt(dados, 4) != 8) {
			System.out.println("Nao e um arquivo MD2: " + file);
			return;
		}

		skinwidth = leInt(dados, 8);
		skinheight = leInt(dados, 12);
		int framesize = leInt(dados, 16);
		numVertices = leInt(dados, 24);
		int numSt = leInt(dados, 28);
		numTris = leInt(dados, 32);
		numFrames = leInt(dados, 40);
		int ofsSt = leInt(dados, 48);
		int ofsTris = leInt(dados, 52);
		int ofsFrames = leInt(dados, 56);

		// ---- coordenadas de textura: pixel -> 0..1 ----
		for (int i = 0; i < numSt; i++) {
			int pos = ofsSt + i * 4;
			Vector2f uv = new Vector2f();
			uv.x = leShort(dados, pos) / (float) skinwidth;
			uv.y = leShort(dados, pos + 2) / (float) skinheight;
			vt.add(uv);
		}

		// ---- triangulos ----
		for (int i = 0; i < numTris; i++) {
			int pos = ofsTris + i * 12;
			Face3D face = new Face3D();
			face.nvertices = 3;
			for (int k = 0; k < 3; k++) {
				face.v[k] = leShort(dados, pos + k * 2);
				face.t[k] = leShort(dados, pos + 6 + k * 2);
			}
			f.add(face);
		}

		// ---- keyframes ----
		for (int i = 0; i < numFrames; i++) {
			int pos = ofsFrames + i * framesize;
			float sx = leFloat(dados, pos);
			float sy = leFloat(dados, pos + 4);
			float sz = leFloat(dados, pos + 8);
			float tx = leFloat(dados, pos + 12);
			float ty = leFloat(dados, pos + 16);
			float tz = leFloat(dados, pos + 20);

			String nome = "";
			for (int k = 0; k < 16; k++) {
				if (dados[pos + 24 + k] == 0) {
					break;
				}
				nome += (char) dados[pos + 24 + k];
			}
			nomesFrames.add(nome);

			ArrayList<Vector3f> verts = new ArrayList<Vector3f>();
			for (int k = 0; k < numVertices; k++) {
				int pv = pos + 40 + k * 4;
				Vector3f vec = new Vector3f();
				vec.x = (dados[pv] & 0xff) * sx + tx;
				vec.y = (dados[pv + 1] & 0xff) * sy + ty;
				vec.z = (dados[pv + 2] & 0xff) * sz + tz;
				// dados[pv+3] = normalIndex (tabela do Quake) -> calculamos a normal na mao

				if (i == 0) {
					if (bbox_min == null) {
						bbox_min = new Vector3f(vec);
						bbox_max = new Vector3f(vec);
					} else {
						bbox_min.x = Math.min(bbox_min.x, vec.x);
						bbox_min.y = Math.min(bbox_min.y, vec.y);
						bbox_min.z = Math.min(bbox_min.z, vec.z);

						bbox_max.x = Math.max(bbox_max.x, vec.x);
						bbox_max.y = Math.max(bbox_max.y, vec.y);
						bbox_max.z = Math.max(bbox_max.z, vec.z);
					}
				}
				verts.add(vec);
			}
			frames.add(verts);
		}

		System.out.println("MD2 " + file + " vertices " + numVertices + " triangulos " + numTris + " frames " + numFrames);
	}

	// little-endian: o byte menos significativo vem primeiro
	int leInt(byte d[], int pos) {
		return (d[pos] & 0xff) | ((d[pos + 1] & 0xff) << 8) | ((d[pos + 2] & 0xff) << 16) | ((d[pos + 3] & 0xff) << 24);
	}

	int leShort(byte d[], int pos) {
		return (d[pos] & 0xff) | ((d[pos + 1] & 0xff) << 8);
	}

	float leFloat(byte d[], int pos) {
		return Float.intBitsToFloat(leInt(d, pos));
	}

	// ================== TEXTURA (skin .pcx) ==================

	/*
	 * Le a skin PCX do Quake 2 (o ImageIO nao le PCX).
	 *
	 * Header de 128 bytes: byte 0 = 0x0A, byte 3 = bits por pixel (8),
	 * bytes 4..11 = xmin, ymin, xmax, ymax, byte 65 = planos (1), bytes 66,67 = bytes por linha.
	 *
	 * Dados em RLE: se o byte >= 0xC0, (byte & 0x3F) = repeticoes e o proximo byte e a cor;
	 * senao o byte e a propria cor.
	 * Paleta: ultimos 769 bytes = 0x0C + 256 cores RGB.
	 */
	public BufferedImage loadPcx(String file) {
		byte d[] = null;
		try {
			File arq = new File(file);
			FileInputStream fin = new FileInputStream(arq);
			d = new byte[(int) arq.length()];
			int lidos = 0;
			while (lidos < d.length) {
				int n = fin.read(d, lidos, d.length - lidos);
				if (n < 0) {
					break;
				}
				lidos += n;
			}
			fin.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		if ((d[0] & 0xff) != 0x0A || (d[3] & 0xff) != 8 || (d[65] & 0xff) != 1) {
			System.out.println("PCX nao suportado (precisa ser 8 bits com paleta): " + file);
			return null;
		}

		int xmin = leShort(d, 4);
		int ymin = leShort(d, 6);
		int xmax = leShort(d, 8);
		int ymax = leShort(d, 10);
		int bytesPorLinha = leShort(d, 66);
		int w = xmax - xmin + 1;
		int h = ymax - ymin + 1;

		// ---- descompacta o RLE ----
		byte indices[] = new byte[bytesPorLinha * h];
		int pos = 128;
		int saida = 0;
		while (saida < indices.length && pos < d.length) {
			int c = d[pos] & 0xff;
			pos++;
			if ((c & 0xC0) == 0xC0) {
				int repeticoes = c & 0x3F;
				byte cor = d[pos];
				pos++;
				for (int i = 0; i < repeticoes && saida < indices.length; i++) {
					indices[saida] = cor;
					saida++;
				}
			} else {
				indices[saida] = (byte) c;
				saida++;
			}
		}

		// ---- paleta ----
		int pal = d.length - 768;
		if ((d[pal - 1] & 0xff) != 0x0C) {
			System.out.println("PCX sem paleta: " + file);
			return null;
		}

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		textura = new int[w * h]; // copia da skin para o preenchimento dos triangulos ler a cor
		texW = w;
		texH = h;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int idx = indices[y * bytesPorLinha + x] & 0xff;
				int r = d[pal + idx * 3] & 0xff;
				int g = d[pal + idx * 3 + 1] & 0xff;
				int b = d[pal + idx * 3 + 2] & 0xff;
				img.setRGB(x, y, 0xff000000 | (r << 16) | (g << 8) | b);
				textura[y * w + x] = (r << 16) | (g << 8) | b;
			}
		}
		return img;
	}

	// ================== ANIMACOES ==================

	/*
	 * Frames com o mesmo nome sem os numeros do final formam uma animacao:
	 * stand01..stand04 -> "stand", run01..run04 -> "run" ...
	 * (no Quake 2 "pain101".."pain204": 3 digitos, o 1o faz parte do nome -> "pain1", "pain2")
	 */
	public void decodeAnimacoes() {
		for (int i = 0; i < nomesFrames.size(); i++) {
			String nome = nomeAnimacao(nomesFrames.get(i));

			if (animacoes.size() > 0 && animacoes.get(animacoes.size() - 1).nome.equals(nome)) {
				animacoes.get(animacoes.size() - 1).ffinal = i;
			} else {
				GrupoFaces anim = new GrupoFaces();
				anim.nome = nome;
				anim.finicial = i;
				anim.ffinal = i;
				animacoes.add(anim);
			}
		}

		for (int i = 0; i < animacoes.size(); i++) {
			GrupoFaces anim = animacoes.get(i);
			System.out.println("animacao " + (i + 1) + ": " + anim.nome + " frames " + anim.finicial + " a " + anim.ffinal);
		}
	}

	String nomeAnimacao(String nomeFrame) {
		int fim = nomeFrame.length();
		while (fim > 0 && nomeFrame.charAt(fim - 1) >= '0' && nomeFrame.charAt(fim - 1) <= '9') {
			fim--;
		}
		if (fim == 0) {
			return nomeFrame;
		}
		if (nomeFrame.length() - fim >= 3) {
			fim++;
		}
		return nomeFrame.substring(0, fim);
	}

	public void setAnimacao(int i) {
		if (i < 0) {
			i = animacoes.size() - 1;
		}
		if (i >= animacoes.size()) {
			i = 0;
		}
		animAtual = i;
		tempo = 0;
		System.out.println("animacao: " + animacoes.get(i).nome);
	}

	/*
	 * Avanca o tempo e escolhe os dois keyframes (A e B) e o t da interpolacao.
	 * diftime em segundos.
	 */
	public void atualizaAnimacao(float diftime) {
		GrupoFaces anim = animacoes.get(animAtual);
		int tamanho = anim.ffinal - anim.finicial + 1;
		boolean repete = !anim.nome.startsWith("death") && !anim.nome.startsWith("crdeath"); // morte para no fim

		if (!pausa) {
			tempo += diftime * fps;
		}

		if (repete) {
			while (tempo >= tamanho) {
				tempo -= tamanho;
			}
		} else if (tempo > tamanho - 1) {
			tempo = tamanho - 1;
		}

		int parteInteira = (int) tempo;
		frameA = anim.finicial + parteInteira;
		if (parteInteira + 1 < tamanho) {
			frameB = frameA + 1;
		} else if (repete) {
			frameB = anim.finicial; // ultimo frame interpola com o primeiro
		} else {
			frameB = frameA;
		}

		if (interpola) {
			t = tempo - parteInteira;
		} else {
			t = 0;
		}

		interpolaFrames();
	}

	/*
	 * INTERPOLACAO LINEAR entre os keyframes A e B, vertice por vertice:
	 *     p = a + (b - a) * t
	 */
	public void interpolaFrames() {
		ArrayList<Vector3f> fa = frames.get(frameA);
		ArrayList<Vector3f> fb = frames.get(frameB);

		for (int i = 0; i < numVertices; i++) {
			Vector3f a = fa.get(i);
			Vector3f b = fb.get(i);
			Vector3f p = posicoes.get(i);
			p.x = a.x + (b.x - a.x) * t;
			p.y = a.y + (b.y - a.y) * t;
			p.z = a.z + (b.z - a.z) * t;
		}
	}

	// ================== DESENHO (por software) ==================

	/*
	 * Transforma cada vertice com a Mat4x4 (feita a mao), projeta em perspectiva
	 * e desenha os triangulos no bufferDeVideo do Main.
	 *
	 * Ordem das transformacoes (aplicadas uma depois da outra no ponto, como no
	 * rotacionaEmTornoDoEixo do Ponto3D):
	 *   1) translada o centro do modelo para a origem
	 *   2) escala para caber na tela
	 *   3) gira -90 em X: o Quake usa Z para cima, a tela usa Y para cima
	 *   4) gira em Y (o modelo rodando)
	 *   5) gira em X (camera um pouco de cima)
	 * Depois a PROJECAO EM PERSPECTIVA: quanto mais longe (z), menor na tela.
	 */
	public void desenhaSe(float escala, float anguloY, float anguloX, boolean wireframe) {

		Mat4x4 mCentro = new Mat4x4();
		mCentro.setTranslate(-(bbox_min.x + bbox_max.x) / 2, -(bbox_min.y + bbox_max.y) / 2, -(bbox_min.z + bbox_max.z) / 2);
		Mat4x4 mEscala = new Mat4x4();
		mEscala.setSacale(escala, escala, escala);
		Mat4x4 mQuake = new Mat4x4();
		mQuake.setRotateX(-90);
		Mat4x4 mRotY = new Mat4x4();
		mRotY.setRotateY(anguloY);
		Mat4x4 mRotX = new Mat4x4();
		mRotX.setRotateX(anguloX);

		// camera: o modelo fica 'distancia' unidades a frente dela
		float distancia = 4;
		float focal = (Main.H / 2) / 0.7f; // mesmo campo de visao do glFrustum do Main original

		// ---- 1) transforma e projeta todos os vertices ----
		Ponto3D tela[] = new Ponto3D[numVertices];
		Ponto3D mundo[] = new Ponto3D[numVertices];
		for (int i = 0; i < numVertices; i++) {
			Vector3f v = posicoes.get(i);
			Ponto3D p = new Ponto3D(v.x, v.y, v.z);
			p.multiplicaMat(mCentro);
			p.multiplicaMat(mEscala);
			p.multiplicaMat(mQuake);
			p.multiplicaMat(mRotY);
			p.multiplicaMat(mRotX);
			mundo[i] = p;

			// perspectiva: divide x e y pela profundidade
			float prof = distancia - p.z;
			float xs = Main.W / 2 + (p.x * focal) / prof;
			float ys = Main.H / 2 - (p.y * focal) / prof; // na tela o y cresce para baixo
			tela[i] = new Ponto3D(xs, ys, prof);
		}

		// ---- 2) desenha cada triangulo ----
		for (int i = 0; i < f.size(); i++) {
			Face3D face = f.get(i);
			Ponto3D a = tela[face.v[0]];
			Ponto3D b = tela[face.v[1]];
			Ponto3D c = tela[face.v[2]];

			if (wireframe) {
				// arestas com o Bresenham
				Main.desenhaLinha((int) a.x, (int) a.y, (int) b.x, (int) b.y, 0, 0, 0);
				Main.desenhaLinha((int) b.x, (int) b.y, (int) c.x, (int) c.y, 0, 0, 0);
				Main.desenhaLinha((int) c.x, (int) c.y, (int) a.x, (int) a.y, 0, 0, 0);
				continue;
			}

			// iluminacao: normal do triangulo (produto vetorial) . direcao da camera (0,0,1)
			Ponto3D ma = mundo[face.v[0]];
			Ponto3D mb = mundo[face.v[1]];
			Ponto3D mc = mundo[face.v[2]];
			float ux = mb.x - ma.x, uy = mb.y - ma.y, uz = mb.z - ma.z;
			float wx = mc.x - ma.x, wy = mc.y - ma.y, wz = mc.z - ma.z;
			float nx = uy * wz - uz * wy;
			float ny = uz * wx - ux * wz;
			float nz = ux * wy - uy * wx;
			float tam = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			float dot = 0;
			if (tam > 0) {
				dot = Math.abs(nz / tam);
			}
			float luz = 0.4f + 0.6f * dot;

			Vector2f ta = vt.get(face.t[0]);
			Vector2f tb = vt.get(face.t[1]);
			Vector2f tc = vt.get(face.t[2]);

			Main.preencheTriangulo(a, b, c, ta, tb, tc, textura, texW, texH, luz);
		}
	}

	public float[] boundingbox() {
		float bbox[] = {bbox_min.x,bbox_min.y,bbox_min.z,bbox_max.x-bbox_min.x,bbox_max.y-bbox_min.y,bbox_max.z-bbox_min.z};
		return bbox;
	}

	public String drawboundingbox() {
		float bbox[] = boundingbox();
		return "["+bbox[0]+","+bbox[1]+","+bbox[2]+","+bbox[3]+","+bbox[4]+","+bbox[5]+"]";
	}
}
