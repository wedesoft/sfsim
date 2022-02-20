#version 410 core
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
void main(void)
{
	gl_Position = gl_in[0].gl_Position;
	EmitVertex();
	gl_Position = gl_in[1].gl_Position;
	EmitVertex();
	gl_Position = gl_in[2].gl_Position;
	EmitVertex();
	EndPrimitive();
}
