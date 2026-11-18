package com.ga.HomeHub.auth.service;

import com.ga.HomeHub.auth.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.auth.dto.user.UpdateUserRequest;
import com.ga.HomeHub.auth.dto.user.UserResponse;
import com.ga.HomeHub.auth.exception.InformationNotFoundException;
import com.ga.HomeHub.auth.exception.UnauthorizedException;
import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.model.enums.UserStatus;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.ga.HomeHub.auth.storage.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    //Delegate local and S3 storage to the storage adapter.
    private final ProfileImageStorage storage;
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

    //Save the profile URL and audit record in the same database transaction.
    @Transactional
    public String picture(MultipartFile file){
        User u=currentUserService.getCurrentUser();

        if(file==null || file.isEmpty() || file.getSize()>5_000_000){
            throw new IllegalArgumentException("Profile picture must be JPG, PNG or WEBP and <= 5MB");
        }

        try{
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
            //Store the image through the configured local or S3 adapter.
            String url=storage.save(name,file.getBytes(),extension);

            u.setProfilePictureUrl(url);
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