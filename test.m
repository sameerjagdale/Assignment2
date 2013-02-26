A=zeros(3,4);
B=ones(3,4);
ii=0;
for jj=1:3;
A=A.^2+B.^2;
end;
ii=ii+1;
A=A+B
if ii==0;
A(ii)=0;
ii=ii+1;
end;
while ii<3;
A=B;
end;
A=B

