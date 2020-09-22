/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package framework;

/**
 * @author gbarbieri
 */
abstract public class Semantic {

    public interface Attr {

        int POSITION = 0;
        int COLOR = 1;
        int NORMAL = 2;
        int TEXCOORD = 3;
        int DRAW_ID = 4;
    }

    public interface Buffer {

        int STATIC = 0;
        int DYNAMIC = 1;
    }

    public interface Frag {

        int COLOR = 0;
        int RED = 0;
        int GREEN = 1;
        int BLUE = 2;
        int ALPHA = 0;
    }

    public interface Image {

        int DIFFUSE = 0;
        int PICKING = 1;
    }

    public interface Object {

        int VAO = 0;
        int VBO = 1;
        int IBO = 2;
        int TEXTURE = 3;
        int SAMPLER = 4;
        int SIZE = 5;
    }

    public interface Renderbuffer {

        int DEPTH = 0;
        int COLOR0 = 1;
    }

    public interface Sampler {

        int DIFFUSE = 0;
        int POSITION = 4;
        int TEXCOORD = 5;
        int COLOR = 6;
    }

    public interface Storage {

        int VERTEX = 0;
    }

    public interface Uniform {

        int MATERIAL = 0;
        int TRANSFORM0 = 1;
        int TRANSFORM1 = 2;
        int INDIRECTION = 3;
        int GLOBAL_MATRICES = 4;
        int CONSTANT = 0;
        int PER_FRAME = 1;
        int PER_PASS = 2;
        int LIGHT = 3;
    }

    public interface Vert {

        int POSITION = 0;
        int COLOR = 3;
        int TEXCOORD = 4;
        int INSTANCE = 7;
    }

    public interface Stream {

        int A = 0;
        int B = 1;
    }
}
