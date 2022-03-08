#version 410 core
layout(triangles) in;
in TES_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
} geo_in[3];
layout(triangle_strip, max_vertices = 3) out;
out GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
} geo_out;
void main(void)
{
  gl_Position = gl_in[0].gl_Position;
  geo_out.colorcoord = geo_in[0].colorcoord;
  geo_out.heightcoord = geo_in[0].heightcoord;
  EmitVertex();
  gl_Position = gl_in[1].gl_Position;
  geo_out.colorcoord = geo_in[1].colorcoord;
  geo_out.heightcoord = geo_in[1].heightcoord;
  EmitVertex();
  gl_Position = gl_in[2].gl_Position;
  geo_out.colorcoord = geo_in[2].colorcoord;
  geo_out.heightcoord = geo_in[2].heightcoord;
  EmitVertex();
  EndPrimitive();
}
