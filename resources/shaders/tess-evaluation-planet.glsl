#version 410 core
layout(quads, equal_spacing, ccw) in;
in mediump vec2 heightcoord_tes[];
in mediump vec2 colorcoord_tes[];
out mediump vec2 heightcoord_geo;
out mediump vec2 colorcoord_geo;
void main()
{
  vec2 heightcoord_a = mix(heightcoord_tes[0], heightcoord_tes[1], gl_TessCoord.x);
  vec2 heightcoord_b = mix(heightcoord_tes[3], heightcoord_tes[2], gl_TessCoord.x);
  heightcoord_geo = mix(heightcoord_a, heightcoord_b, gl_TessCoord.y);
  vec2 colorcoord_a = mix(colorcoord_tes[0], colorcoord_tes[1], gl_TessCoord.x);
  vec2 colorcoord_b = mix(colorcoord_tes[3], colorcoord_tes[2], gl_TessCoord.x);
  colorcoord_geo = mix(colorcoord_a, colorcoord_b, gl_TessCoord.y);
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  gl_Position = mix(a, b, gl_TessCoord.y);
}
