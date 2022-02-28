#version 410 core
layout(quads, equal_spacing, ccw) in;
uniform mat4 projection;
uniform mat4 transform;
in TCS_OUT
{
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} tes_in[];
out TES_OUT
{
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} tes_out;
void main()
{
  vec2 heightcoord_a = mix(tes_in[0].heightcoord, tes_in[1].heightcoord, gl_TessCoord.x);
  vec2 heightcoord_b = mix(tes_in[3].heightcoord, tes_in[2].heightcoord, gl_TessCoord.x);
  tes_out.heightcoord = mix(heightcoord_a, heightcoord_b, gl_TessCoord.y);
  vec2 colorcoord_a = mix(tes_in[0].colorcoord, tes_in[1].colorcoord, gl_TessCoord.x);
  vec2 colorcoord_b = mix(tes_in[3].colorcoord, tes_in[2].colorcoord, gl_TessCoord.x);
  tes_out.colorcoord = mix(colorcoord_a, colorcoord_b, gl_TessCoord.y);
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  vec4 point = mix(a, b, gl_TessCoord.y);
  gl_Position = projection * transform * point;
}
