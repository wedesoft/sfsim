#version 410 core

layout(quads, equal_spacing, ccw) in;

uniform sampler2D surface;
uniform mat4 projection;
uniform vec3 tile_center;
uniform mat4 recenter_and_transform;

in TCS_OUT
{
  vec2 heightcoord;
  vec2 colorcoord;
} tes_in[];

out TES_OUT
{
  vec2 colorcoord;
  vec3 point;
} tes_out;

// Use surface pointcloud to determine coordinates of tessellated points.
void main()
{
  vec2 colorcoord_a = mix(tes_in[0].colorcoord, tes_in[1].colorcoord, gl_TessCoord.x);
  vec2 colorcoord_b = mix(tes_in[3].colorcoord, tes_in[2].colorcoord, gl_TessCoord.x);
  tes_out.colorcoord = mix(colorcoord_a, colorcoord_b, gl_TessCoord.y);
  vec2 heightcoord_a = mix(tes_in[0].heightcoord, tes_in[1].heightcoord, gl_TessCoord.x);
  vec2 heightcoord_b = mix(tes_in[3].heightcoord, tes_in[2].heightcoord, gl_TessCoord.x);
  vec2 heightcoord = mix(heightcoord_a, heightcoord_b, gl_TessCoord.y);
  vec3 vector = texture(surface, heightcoord).xyz;
  tes_out.point = tile_center + vector;
  vec4 transformed_point = recenter_and_transform * vec4(vector, 1);
  gl_Position = projection * transformed_point;
}
