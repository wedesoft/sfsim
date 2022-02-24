#version 410 core
layout(triangles) in;
in mediump vec2 heightcoord_geo[3];
in mediump vec2 colorcoord_geo[3];
layout(triangle_strip, max_vertices = 3) out;
out mediump vec2 heightcoord_frag;
out mediump vec2 colorcoord_frag;
void main(void)
{
  gl_Position = gl_in[0].gl_Position;
  heightcoord_frag = heightcoord_geo[0];
  colorcoord_frag = colorcoord_geo[0];
  EmitVertex();
  gl_Position = gl_in[1].gl_Position;
  heightcoord_frag = heightcoord_geo[1];
  colorcoord_frag = colorcoord_geo[1];
  EmitVertex();
  gl_Position = gl_in[2].gl_Position;
  heightcoord_frag = heightcoord_geo[2];
  colorcoord_frag = colorcoord_geo[2];
  EmitVertex();
  EndPrimitive();
}
