import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyB6y8XoNC5phpAxYyZi_jryYl9wW8Waj-I",
  authDomain: "ps-restaurante.firebaseapp.com",
  projectId: "ps-restaurante",
  storageBucket: "ps-restaurante.firebasestorage.app",
  messagingSenderId: "721036717599",
  appId: "1:721036717599:web:1cfcf424448d8efe936f2a"
};

const firebaseApp = initializeApp(firebaseConfig);

export const firebaseAuth = getAuth(firebaseApp);
export const firebaseDb = getFirestore(firebaseApp);
