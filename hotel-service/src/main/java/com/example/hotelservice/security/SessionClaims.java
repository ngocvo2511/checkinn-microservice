package com.example.hotelservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.UUID;

/** Metadata lookup only: callers must still verify JWT signature, expiry and permissions. */
final class SessionClaims {
    private static final ObjectMapper JSON=new ObjectMapper();
    record User(UUID id,String generation) {}
    record Token(boolean valid,User user) {}
    static Token read(String token) {
        try {
            String[] parts=token.split("\\.",-1);
            if(parts.length!=3)return new Token(false,null);
            var claims=JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            var subject=claims.get("sub");if(subject==null||!subject.isTextual()||subject.asText().isBlank())return new Token(false,null);
            UUID id;
            try{id=UUID.fromString(subject.asText());}catch(IllegalArgumentException ex){return new Token(true,null);}
            var value=claims.get("sessionGeneration");String generation="";
            if(value!=null&&!value.isNull()) {
                if(!value.isTextual())return new Token(false,null);
                generation=value.asText();if(!generation.isEmpty())UUID.fromString(generation);
            }
            return new Token(true,new User(id,generation));
        }catch(Exception ex){return new Token(false,null);}
    }
}
