#version 410 core

layout(quads, equal_spacing, ccw) in;

uniform sampler2D surface;
uniform mat4 projection;
uniform vec3 tile_center;
uniform mat4 tile_to_camera;

in TCS_OUT
{
  vec2 surfacecoord;
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
  vec2 surfacecoord_a = mix(tes_in[0].surfacecoord, tes_in[1].surfacecoord, gl_TessCoord.x);
  vec2 surfacecoord_b = mix(tes_in[3].surfacecoord, tes_in[2].surfacecoord, gl_TessCoord.x);
  vec2 surfacecoord = mix(surfacecoord_a, surfacecoord_b, gl_TessCoord.y);
  vec3 vector = texture(surface, surfacecoord).xyz;
  tes_out.point = tile_center + vector;
  vec4 transformed_point = tile_to_camera * vec4(vector, 1);
  gl_Position = projection * transformed_point;
}
