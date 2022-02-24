#version 410 core
layout(vertices = 4) out;
uniform int high_detail;
uniform int low_detail;
uniform int neighbours;
in mediump vec2 heightcoord_tcs[];
in mediump vec2 colorcoord_tcs[];
out mediump vec2 heightcoord_tes[];
out mediump vec2 colorcoord_tes[];
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
  heightcoord_tes[gl_InvocationID] = heightcoord_tcs[gl_InvocationID];
  colorcoord_tes[gl_InvocationID] = colorcoord_tcs[gl_InvocationID];
}
