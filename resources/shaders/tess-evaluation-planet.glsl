#version 410 core
layout(quads, equal_spacing, ccw) in;
void main()
{
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  gl_Position = mix(a, b, gl_TessCoord.y);
}
