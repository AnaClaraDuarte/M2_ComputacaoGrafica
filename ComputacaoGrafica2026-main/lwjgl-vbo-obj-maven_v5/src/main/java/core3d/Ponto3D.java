package core3d;

public class Ponto3D {
	public float x;
	public float y;
	public float z;
	public float w;
	
	public Ponto3D(Ponto3D p) {
		x = p.x;
		y = p.y;
		z = p.z;
		w = p.w;
	}
	
	public Ponto3D(float x, float y,float z) {
		super();
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = 1;
	}
	
	

	public Ponto3D(float x, float y,float z, float w) {
		super();
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}



	public void multiplicaMat(Mat4x4 mat) {
		float x1 = mat.mat[0][0]*x +mat.mat[0][1]*y +mat.mat[0][2]*z +mat.mat[0][3]*w;
		float y1 = mat.mat[1][0]*x +mat.mat[1][1]*y +mat.mat[1][2]*z +mat.mat[1][3]*w;
		float z1 = mat.mat[2][0]*x +mat.mat[2][1]*y +mat.mat[2][2]*z +mat.mat[2][3]*w;
		float w1 = mat.mat[3][0]*x +mat.mat[3][1]*y +mat.mat[3][2]*z +mat.mat[3][3]*w;
		
		//System.out.println("x "+x+" "+x1);
		//System.out.println("y "+y+" "+y1);
		//System.out.println("z "+z+" "+z1);
		
		x = x1;
		y = y1;
		z = z1;
		w = w1;
		
		
	}
	
//	public void translacao(float a,float b) {
//		x+=a;
//		y+=b;
//	}
//	public void escala(float a,float b) {
//		x=x*a;
//		y=y*b;
//	}
//	public void rotacao(float ang) {
//		float rad = ang*0.017453f;
//		float sin = (float)Math.sin(rad);
//		float cos = (float)Math.cos(rad);
//		float nx = x*cos+y*sin;
//		float ny = -x*sin+y*cos;
//		x = nx;
//		y = ny;
//	}
	
	/**
	 * Gira este ponto por 'anguloGraus' graus em torno de um eixo (uma reta)
	 * delimitado por dois pontos: eixoA e eixoB.
	 * 
	 * Ideia (algoritmo classico de rotacao em torno de eixo arbitrario):
	 *  1) Translada tudo para que eixoA fique na origem;
	 *  2) Gira em torno de X e depois em torno de Y ate o eixo (A->B) ficar
	 *     alinhado com o eixo Z;
	 *  3) Com o eixo alinhado a Z, a rotacao pedida vira uma simples
	 *     rotacao em Z (facil, pois Z ja "eh" o proprio eixo);
	 *  4) Desfaz o alinhamento (Y e depois X) e desfaz a translacao.
	 */
	public void rotacionaEmTornoDoEixo(Ponto3D eixoA, Ponto3D eixoB, float anguloGraus) {
		

		float ex = eixoB.x - eixoA.x;
		float ey = eixoB.y - eixoA.y;
		float ez = eixoB.z - eixoA.z;
		
		float tamanho = (float)Math.sqrt(ex*ex + ey*ey + ez*ez);
		
		if (tamanho == 0) {

			return;
		}

		ex /= tamanho;
		ey /= tamanho;
		ez /= tamanho;

		float anguloX = (float)Math.toDegrees(Math.atan2(ey, ez));

		float d = (float)Math.sqrt(ey*ey + ez*ez);

		float anguloY = (float)Math.toDegrees(Math.atan2(ex, d));
		
		Mat4x4 mTranslada = new Mat4x4();
		mTranslada.setTranslate(-eixoA.x, -eixoA.y, -eixoA.z);
		
		Mat4x4 mRotX = new Mat4x4();
		mRotX.setRotateX(anguloX);
		
		Mat4x4 mRotY = new Mat4x4();
		mRotY.setRotateY(anguloY);
		
		Mat4x4 mRotZ = new Mat4x4();
		mRotZ.setRotateZ(anguloGraus);
		
		Mat4x4 mRotYVolta = new Mat4x4();
		mRotYVolta.setRotateY(-anguloY);
		
		Mat4x4 mRotXVolta = new Mat4x4();
		mRotXVolta.setRotateX(-anguloX);
		
		Mat4x4 mTranslaVolta = new Mat4x4();
		mTranslaVolta.setTranslate(eixoA.x, eixoA.y, eixoA.z);

		this.multiplicaMat(mTranslada);
		this.multiplicaMat(mRotX);
		this.multiplicaMat(mRotY);
		this.multiplicaMat(mRotZ);
		this.multiplicaMat(mRotYVolta);
		this.multiplicaMat(mRotXVolta);
		this.multiplicaMat(mTranslaVolta);
	}
	
	/**
	 * Gira este ponto em torno de um UNICO ponto pivo (por exemplo, marcado
	 * com um clique na tela). O eixo de rotacao usado eh a reta paralela ao
	 * eixo Z que passa por esse pivo (ou seja, o eixo "delimitado por dois
	 * pontos" onde o segundo ponto eh o proprio pivo deslocado em Z) --
	 * na pratica, isso equivale a girar ao redor do pivo dentro do plano da tela.
	 */
	public void rotacionaEmTornoDoPontoZ(Ponto3D pivo, float anguloGraus) {
		Ponto3D outroPontoDoEixo = new Ponto3D(pivo.x, pivo.y, pivo.z + 1);
		this.rotacionaEmTornoDoEixo(pivo, outroPontoDoEixo, anguloGraus);
	}
}
