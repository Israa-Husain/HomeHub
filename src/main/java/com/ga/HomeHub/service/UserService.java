
package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.dto.user.UpdateUserRequest;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final Logger logger=Logger.getLogger(UserService.class.getName());

    public UserResponse getCurrentUserProfile(){
        return UserResponse.from(currentUserService.getCurrentUser());
    }

    @Transactional
    public UserResponse update(UpdateUserRequest updateUserRequest){
        User user=currentUserService.getCurrentUser();
        user.setFirstName(updateUserRequest.firstName());
        user.setLastName(updateUserRequest.lastName());
        user.setPhoneNumber(updateUserRequest.phoneNumber());

        userRepository.save(user);
        auditLogService.record(user,"Update profile","User",user.getId(),"User Updated Profile");

        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest newPassword){
        User user=currentUserService.getCurrentUser();

        if(!passwordEncoder.matches(newPassword.currentPassword(),user.getPasswordHash())){
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword.newPassword()));
        userRepository.save(user);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable){
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    public String picture(MultipartFile file){
        User u=currentUserService.getCurrentUser();

        if(file==null || file.isEmpty() || file.getSize()>5_000_000){
            throw new IllegalArgumentException("Profile picture must be JPG, PNG or WEBP and <= 5MB");
        }

        try{
            Path dir=Paths.get("uploads");
            Files.createDirectories(dir);

            //Detect the actual image signature instead of trusting the browser content type.
            String extension;
            try(InputStream input=file.getInputStream()){
                byte[] header=input.readNBytes(12);
                if(header.length>=3 && (header[0]&0xff)==0xff && (header[1]&0xff)==0xd8 && (header[2]&0xff)==0xff){
                    extension=".jpg";
                }else if(header.length>=8 && (header[0]&0xff)==0x89 && header[1]==0x50 && header[2]==0x4e && header[3]==0x47
                        && header[4]==0x0d && header[5]==0x0a && header[6]==0x1a && header[7]==0x0a){
                    extension=".png";
                }else if(header.length>=12 && header[0]=='R' && header[1]=='I' && header[2]=='F' && header[3]=='F'
                        && header[8]=='W' && header[9]=='E' && header[10]=='B' && header[11]=='P'){
                    extension=".webp";
                }else{
                    throw new IllegalArgumentException("File contents must be JPG, PNG or WEBP");
                }
            }

            String name=u.getId()+"-"+UUID.randomUUID()+extension;
            Files.copy(file.getInputStream(),dir.resolve(name),StandardCopyOption.REPLACE_EXISTING);

            u.setProfilePictureUrl("/uploads/"+name);
            userRepository.save(u);
            auditLogService.record(u,"Update Profile Picture","User",u.getId(),"User Updated Profile Picture");

            return u.getProfilePictureUrl();
        }catch(IOException e){
            throw new RuntimeException("Could not store file",e);
        }
    }

    @Transactional
    public void deactivate(Long id){
        User user=userRepository.findById(id).orElseThrow(()->new InformationNotFoundException("User not found"));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        logger.log(Level.INFO,"Admin deactivated user");
        auditLogService.record(currentUserService.getCurrentUser(),"Deactivate User","User",id,"Admin deactivated the user.");
    }
}
