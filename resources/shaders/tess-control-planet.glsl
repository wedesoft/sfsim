#version 410 core
layout(vertices = 4) out;
uniform int high_detail;
uniform int low_detail;
uniform int neighbours;
in VS_OUT
{
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} tcs_in[];
out TCS_OUT
{
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} tcs_out[];
void main(void)
{
  if (gl_InvocationID == 0) {
    if ((neighbours & 1) != 0) {
      gl_TessLevelOuter[0] = high_detail;
    } else {
      gl_TessLevelOuter[0] = low_detail;
    };
    if ((neighbours & 2) != 0) {
      gl_TessLevelOuter[1] = high_detail;
    } else {
      gl_TessLevelOuter[1] = low_detail;
    };
    if ((neighbours & 4) != 0) {
      gl_TessLevelOuter[2] = high_detail;
    } else {
      gl_TessLevelOuter[2] = low_detail;
    };
    if ((neighbours & 8) != 0) {
      gl_TessLevelOuter[3] = high_detail;
    } else {
      gl_TessLevelOuter[3] = low_detail;
    };
    gl_TessLevelInner[0] = high_detail;
    gl_TessLevelInner[1] = high_detail;
  };
  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
  tcs_out[gl_InvocationID].heightcoord = tcs_in[gl_InvocationID].heightcoord;
  tcs_out[gl_InvocationID].colorcoord = tcs_in[gl_InvocationID].colorcoord;
}
