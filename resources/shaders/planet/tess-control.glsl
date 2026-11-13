#version 450 core

layout(vertices = 4) out;

uniform int inner_detail;
uniform int up_detail;
uniform int left_detail;
uniform int down_detail;
uniform int right_detail;

in VS_OUT
{
  vec2 surfacecoord;
  vec2 colorcoord;
} tcs_in[];

out TCS_OUT
{
  vec2 surfacecoord;
  vec2 colorcoord;
} tcs_out[];

// Control amount of tessellation so that it matches with neighbouring patches.
void main(void)
{
  if (gl_InvocationID == 0) {
    gl_TessLevelOuter[0] = up_detail;
    gl_TessLevelOuter[1] = left_detail;
    gl_TessLevelOuter[2] = down_detail;
    gl_TessLevelOuter[3] = right_detail;
    gl_TessLevelInner[0] = inner_detail;
    gl_TessLevelInner[1] = inner_detail;
  };
  tcs_out[gl_InvocationID].surfacecoord = tcs_in[gl_InvocationID].surfacecoord;
  tcs_out[gl_InvocationID].colorcoord = tcs_in[gl_InvocationID].colorcoord;
}
