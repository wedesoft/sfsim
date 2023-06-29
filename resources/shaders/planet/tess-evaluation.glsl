#version 410 core

layout(quads, equal_spacing, ccw) in;

uniform sampler2D heightfield;
uniform mat4 projection;
uniform mat4 inverse_transform;

in TCS_OUT
{
  vec2 heightcoord;
  vec2 colorcoord;
} tes_in[];

out TES_OUT
{
  vec2 colorcoord;
  vec2 heightcoord;
  vec3 point;
} tes_out;

// Use heightfield to determine coordinates of tessellated points.
void main()
{
  vec2 colorcoord_a = mix(tes_in[0].colorcoord, tes_in[1].colorcoord, gl_TessCoord.x);
  vec2 colorcoord_b = mix(tes_in[3].colorcoord, tes_in[2].colorcoord, gl_TessCoord.x);
  tes_out.colorcoord = mix(colorcoord_a, colorcoord_b, gl_TessCoord.y);
  vec2 heightcoord_a = mix(tes_in[0].heightcoord, tes_in[1].heightcoord, gl_TessCoord.x);
  vec2 heightcoord_b = mix(tes_in[3].heightcoord, tes_in[2].heightcoord, gl_TessCoord.x);
  vec2 heightcoord = mix(heightcoord_a, heightcoord_b, gl_TessCoord.y);
  tes_out.heightcoord = heightcoord;
  float scale = texture(heightfield, heightcoord).r;
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  vec3 cube_point = mix(a, b, gl_TessCoord.y).xyz;
  vec3 point = scale * cube_point;
  tes_out.point = point;
  vec4 transformed_point = inverse_transform * vec4(point, 1);
  gl_Position = projection * transformed_point;
}
